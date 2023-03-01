package org.jetbrains.kotlin.com.intellij.openapi.progress.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.com.intellij.openapi.application.Application;
import org.jetbrains.kotlin.com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.kotlin.com.intellij.openapi.application.WriteAction;
import org.jetbrains.kotlin.com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.kotlin.com.intellij.openapi.progress.ProgressIndicatorProvider;
import org.jetbrains.kotlin.com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.kotlin.com.intellij.openapi.progress.util.ProgressIndicatorListener;
import org.jetbrains.kotlin.com.intellij.openapi.util.Key;
import org.jetbrains.kotlin.com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.kotlin.com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import org.jetbrains.kotlin.com.intellij.util.containers.ContainerUtil;
import org.jetbrains.kotlin.com.intellij.util.messages.Topic;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public final class ProgressSuspender implements AutoCloseable {
  private static final Key<ProgressSuspender> PROGRESS_SUSPENDER = Key.create("PROGRESS_SUSPENDER");
  public static final Topic<SuspenderListener>
          TOPIC = new Topic<>("ProgressSuspender", SuspenderListener.class, Topic.BroadcastDirection.NONE);

  private final Object myLock = new Object();
  private static final Application ourApp = ApplicationManager.getApplication();
  @NotNull private final String mySuspendedText;
  @Nullable private String myTempReason;
  private final SuspenderListener myPublisher;
  private volatile boolean mySuspended;
  private final CoreProgressManager.CheckCanceledHook myHook = this::freezeIfNeeded;
  private final Set<ProgressIndicator> myProgresses = Collections.synchronizedSet(new HashSet<>());
  private final Map<ProgressIndicator, Integer> myProgressesInNonSuspendableSections = new ConcurrentHashMap<>();
  private boolean myClosed;

  private ProgressSuspender(@NotNull ProgressIndicatorEx progress, String suspendedText) {
    mySuspendedText = suspendedText;
    assert progress.isRunning();
    assert ProgressIndicatorProvider.getGlobalProgressIndicator() == progress;
    myPublisher = ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC);

    attachToProgress(progress);

    new ProgressIndicatorListener() {
      @Override
      public void cancelled() {
        resumeProcess();
      }
    }.installToProgress(progress);

    myPublisher.suspendableProgressAppeared(this);
  }

  @Override
  public void close() {
    synchronized (myLock) {
      myClosed = true;
      mySuspended = false;
//      ((CoreProgressManager) ProgressManager.getInstance()).removeCheckCanceledHook(myHook);
    }
    for (ProgressIndicator progress : myProgresses) {
      ((UserDataHolder) progress).putUserData(PROGRESS_SUSPENDER, null);
    }
  }

  public static @NotNull ProgressSuspender markSuspendable(@NotNull ProgressIndicator indicator, @NotNull String suspendedText) {
    return new ProgressSuspender((ProgressIndicatorEx)indicator, suspendedText);
  }

  public void executeNonSuspendableSection(@NotNull ProgressIndicator indicator, @NotNull Runnable runnable) {
    myProgressesInNonSuspendableSections.compute(indicator, (__, number) -> (number == null ? 0 : number) + 1);
    try {
      runnable.run();
    }
    finally {
      myProgressesInNonSuspendableSections.compute(indicator, (__, number) -> number == null || number <= 1 ? null : number - 1);
    }
  }

  public static ProgressSuspender getSuspender(@NotNull ProgressIndicator indicator) {
    return indicator instanceof UserDataHolder ? ((UserDataHolder)indicator).getUserData(PROGRESS_SUSPENDER) : null;
  }

  /**
   * Associates an additional progress indicator with this suspender, so that its {@code #checkCanceled} can later block the calling thread.
   */
  public void attachToProgress(@NotNull ProgressIndicatorEx progress) {
    myProgresses.add(progress);
    ((UserDataHolder) progress).putUserData(PROGRESS_SUSPENDER, this);
  }

  @NotNull
  public String getSuspendedText() {
    synchronized (myLock) {
      return myTempReason != null ? myTempReason : mySuspendedText;
    }
  }

  public boolean isSuspended() {
    return mySuspended;
  }

  public boolean isClosed() {
    return myClosed;
  }

  /**
   * @param reason if provided, is displayed in the UI instead of suspended text passed into constructor until the progress is resumed
   */
  public void suspendProcess(@Nullable String reason) {
    synchronized (myLock) {
        if (mySuspended || myClosed) {
            return;
        }

      mySuspended = true;
      myTempReason = reason;

//      ((ProgressManagerImpl)ProgressManager.getInstance()).addCheckCanceledHook(myHook);
    }

    myPublisher.suspendedStatusChanged(this);

    // Give running NonBlockingReadActions a chance to suspend
    ApplicationManager.getApplication().invokeLater(() -> WriteAction.run(() -> {}));
  }

  public void resumeProcess() {
    synchronized (myLock) {
        if (!mySuspended) {
            return;
        }

      mySuspended = false;
      myTempReason = null;

//      ((ProgressManagerImpl)ProgressManager.getInstance()).removeCheckCanceledHook(myHook);

      myLock.notifyAll();
    }

    myPublisher.suspendedStatusChanged(this);
  }

  private boolean freezeIfNeeded(ProgressIndicator current) {
    if (current == null) {
      current = ProgressIndicatorProvider.getGlobalProgressIndicator();
    }
    if (current == null || !myProgresses.contains(current)) {
      return false;
    }

    if (myProgressesInNonSuspendableSections.containsKey(current)) {
      return false;
    }

    if (isCurrentThreadHoldingKnownLocks()) {
      return false;
    }

    synchronized (myLock) {
      while (mySuspended) {
        try {
          myLock.wait(10000);
        }
        catch (InterruptedException ignore) {
        }
      }

      return true;
    }
  }

  private static boolean isCurrentThreadHoldingKnownLocks() {
    if (ourApp.isReadAccessAllowed()) {
      return true;
    }

    ThreadInfo[] infos = ManagementFactory.getThreadMXBean().getThreadInfo(new long[]{Thread.currentThread().getId()}, true, false);
    return infos.length > 0 && infos[0].getLockedMonitors().length > 0;
  }

  public interface SuspenderListener {
    /** Called (on any thread) when a new progress is created with suspension capability */
    default void suspendableProgressAppeared(@NotNull ProgressSuspender suspender) {}

    /** Called (on any thread) when a progress is suspended or resumed */
    default void suspendedStatusChanged(@NotNull ProgressSuspender suspender) {}
  }

}
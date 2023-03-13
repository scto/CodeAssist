package com.tyron.legacyEditor;

import com.tyron.legacyEditor.event.ContentListener;

import org.jetbrains.annotations.NotNull;

public interface Content extends CharSequence {

    /**
     * Checks if this content can be redone to the previous action
     * @return whether this content can be redone
     */
    boolean canRedo();

    /**
     * Redo the previous action, always check if {@link #canRedo()} returns true before calling
     * this method.
     */
    void redo();

    /**
     * @return Whether this content can be reverted to the previous action
     */
    boolean canUndo();

    /**
     * Revert to the previous action
     */
    void undo();

    int getLineCount();

    String getLineString(int line);

    void insert(int line, int column, CharSequence text);

    void insert(int index, CharSequence text);

    void delete(int start, int end);

    void replace(int start, int end, CharSequence text);

    void setData(String key, Object object);

    Object getData(String key);

    void addContentListener(@NotNull ContentListener listener);

    void removeContentListener(@NotNull ContentListener listener);

    void setModificationStamp(long stamp);

    long getModificationStamp();
}

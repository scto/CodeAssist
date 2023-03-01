package org.jetbrains.kotlin.com.intellij.openapi.roots;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.kotlin.com.intellij.openapi.fileTypes.FileTypeRegistry;
import org.jetbrains.kotlin.com.intellij.openapi.module.Module;
import org.jetbrains.kotlin.com.intellij.openapi.project.Project;
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

/**
 * Provides information about files contained in a project. Should be used from a read action.
 *
 * @see ProjectRootManager#getFileIndex()
 */
public interface ProjectFileIndex extends FileIndex {

  /**
   * @deprecated use {@link ProjectFileIndex#getInstance(Project)} instead
   */
  @Deprecated
  final class SERVICE {
    private SERVICE() { }

    public static ProjectFileIndex getInstance(Project project) {
      return ProjectFileIndex.getInstance(project);
    }
  }

  @NonNull
  static ProjectFileIndex getInstance(@NonNull Project project) {
    return project.getService(ProjectFileIndex.class);
  }

  /**
   * Returns {@code true} if {@code file} is located under project content or library roots and not excluded or ignored
   */
  boolean isInProject(@NonNull VirtualFile file);

  /**
   * Returns {@code true} if {@code file} is located under project content or library roots, regardless of whether it's marked as excluded or not,
   * and returns {@code false} if {@code file} is located outside or it or one of its parent directories is ignored.
   */
  boolean isInProjectOrExcluded(@NonNull VirtualFile file);

  /**
   * Returns module to which content the specified file belongs or null if the file does not belong to content of any module.
   */
  @Nullable
  Module getModuleForFile(@NonNull VirtualFile file);

  /**
   * Returns module to which content the specified file belongs or null if the file does not belong to content of any module.
   *
   * @param honorExclusion if {@code false} the containing module will be returned even if the file is located under a folder marked as excluded
   */
  @Nullable
  Module getModuleForFile(@NonNull VirtualFile file, boolean honorExclusion);

  /**
   * Returns the order entries which contain the specified file (either in CLASSES or SOURCES).
   */
  @NonNull
  List<OrderEntry> getOrderEntriesForFile(@NonNull VirtualFile file);

  /**
   * Returns a classpath entry to which the specified file or directory belongs.
   *
   * @return the file for the classpath entry, or null if the file is not a compiled
   *         class file or directory belonging to a library.
   */
  @Nullable
  VirtualFile getClassRootForFile(@NonNull VirtualFile file);

  /**
   * Returns the module source root or library source root to which the specified file or directory belongs.
   *
   * @return the file for the source root, or null if the file is not located under any of the source roots for the module.
   */
  @Nullable
  VirtualFile getSourceRootForFile(@NonNull VirtualFile file);

  /**
   * Returns the module content root to which the specified file or directory belongs or null if the file does not belong to content of any module.
   */
  @Nullable
  VirtualFile getContentRootForFile(@NonNull VirtualFile file);

  /**
   * Returns the module content root to which the specified file or directory belongs or null if the file does not belong to content of any module.
   *
   * @param honorExclusion if {@code false} the containing content root will be returned even if the file is located under a folder marked as excluded
   */
  @Nullable
  VirtualFile getContentRootForFile(@NonNull VirtualFile file, final boolean honorExclusion);

  /**
   * @deprecated use {@link PackageIndex#getPackageNameByDirectory(VirtualFile)} from Java plugin instead.
   */
  @Deprecated
  @Nullable
  String getPackageNameByDirectory(@NonNull VirtualFile dir);

  /**
   * Returns true if {@code file} is a file which belongs to the classes (not sources) of some library which is included into dependencies
   * of some module.
   * @deprecated name of this method may be misleading, actually it doesn't check that {@code file} has the 'class' extension. 
   * Use {@link #isInLibraryClasses} with additional {@code !file.isDirectory()} check instead.   
   */
  @Deprecated
  boolean isLibraryClassFile(@NonNull VirtualFile file);

  /**
   * Returns true if {@code fileOrDir} is a file or directory from production/test sources of some module or sources of some library which is included into dependencies
   * of some module.
   */
  boolean isInSource(@NonNull VirtualFile fileOrDir);

  /**
   * Returns true if {@code fileOrDir} belongs to classes of some library which is included into dependencies of some module.
   */
  boolean isInLibraryClasses(@NonNull VirtualFile fileOrDir);

  /**
   * @return true if the file belongs to the classes or sources of a library added to dependencies of the project,
   *         false otherwise
   */
  boolean isInLibrary(@NonNull VirtualFile fileOrDir);

  /**
   * Returns true if {@code fileOrDir} is a file or directory from sources of some library which is included into dependencies
   * of some module.
   */
  boolean isInLibrarySource(@NonNull VirtualFile fileOrDir);

  /**
   * @deprecated name of this method may be confusing. If you want to check if the file is excluded or ignored use {@link #isExcluded(VirtualFile)}.
   * If you want to check if the file is ignored use {@link FileTypeRegistry#isFileIgnored(VirtualFile)}.
   * If you want to check if the file or one of its parents is ignored use {@link #isUnderIgnored(VirtualFile)}.
   */
  @Deprecated(forRemoval = true)
  boolean isIgnored(@NonNull VirtualFile file);

  /**
   * Checks if the specified file or directory is located under project roots but the file itself or one of its parent directories is
   * either excluded from the project or ignored by {@link FileTypeRegistry#isFileIgnored(VirtualFile)}).
   *
   * @return true if {@code file} is excluded or ignored, false otherwise.
   */
  boolean isExcluded(@NonNull VirtualFile file);

  /**
   * Checks if the specified file or directory is located under project roots but the file itself or one of its parent directories is ignored
   * by {@link FileTypeRegistry#isFileIgnored(VirtualFile)}).
   *
   * @return true if {@code file} is ignored, false otherwise.
   */
  boolean isUnderIgnored(@NonNull VirtualFile file);
//
//  /**
//   * Returns type of the module source root which contains the given {@code file}, or {@code null} if {@code file} doesn't belong to sources
//   * of modules.
//   */
//  @Nullable JpsModuleSourceRootType<?> getContainingSourceRootType(@NonNull VirtualFile file);

  /**
   * Returns {@code true} if {@code file} is located under a source root which is marked as containing generated sources. This method is 
   * mostly for internal use only. If you need to check if a source file is generated, it's better to use {@link com.intellij.openapi.roots.GeneratedSourcesFilter#isGeneratedSourceByAnyFilter} instead.
   */
  boolean isInGeneratedSources(@NonNull VirtualFile file);

  /**
   * @deprecated use other methods from this class to obtain the information you need to get from {@link SourceFolder} instance, e.g. 
   * {@link #getContainingSourceRootType} or {@link #isInGeneratedSources}.
   */
  @Deprecated
  @Nullable
  default SourceFolder getSourceFolder(@NonNull VirtualFile fileOrDir) {
    return null;
  }

  /**
   * Returns name of the unloaded module to which content {@code fileOrDir} belongs, or {@code null} if {@code fileOrDir} doesn't belong
   * to an unloaded module.
   */
  @Nullable
  String getUnloadedModuleNameForFile(@NonNull VirtualFile fileOrDir);
}
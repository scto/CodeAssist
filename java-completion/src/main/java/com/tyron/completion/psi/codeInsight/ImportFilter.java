package com.tyron.completion.psi.codeInsight;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.kotlin.com.intellij.psi.PsiFile;

/**
 * Allow specifying if fully qualified name should be used for a reference instead of importing class.
 */
public abstract class ImportFilter {
  public static final ExtensionPointName<ImportFilter> EP_NAME = new ExtensionPointName<>("com.intellij.importFilter");

  /**
   * Allow specifying if fully qualified name should be used for a reference instead of importing class.
   * @param targetFile file in which reference is located (or should be located)
   * @param classQualifiedName name of class which should be tested
   */
  public abstract boolean shouldUseFullyQualifiedName(@NotNull PsiFile targetFile, @NotNull String classQualifiedName);

  public static boolean shouldImport(@NotNull PsiFile targetFile, @NotNull String classQualifiedName) {
    for (ImportFilter filter : EP_NAME.getExtensions()) {
      if (filter.shouldUseFullyQualifiedName(targetFile, classQualifiedName)) {
        return false;
      }
    }
    return true;
  }
}
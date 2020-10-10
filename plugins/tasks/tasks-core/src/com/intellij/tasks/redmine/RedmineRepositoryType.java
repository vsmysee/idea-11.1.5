package com.intellij.tasks.redmine;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dennis.Ushakov
 */
public class RedmineRepositoryType extends BaseRepositoryType<RedmineRepository> {
  static final Icon ICON = IconLoader.getIcon("/icons/redmine.png");

  @NotNull
  @Override
  public String getName() {
    return "Redmine";
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @NotNull
  @Override
  public TaskRepository createRepository() {
    return new RedmineRepository(this);
  }

  @Override
  public Class<RedmineRepository> getRepositoryClass() {
    return RedmineRepository.class;
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(RedmineRepository repository,
                                           Project project,
                                           Consumer<RedmineRepository> changeListener) {
    return new RedmineRepositoryEditor(project, repository, changeListener);
  }

  @Override
  protected int getFeatures() {
    return BASIC_HTTP_AUTHORIZATION;
  }
}

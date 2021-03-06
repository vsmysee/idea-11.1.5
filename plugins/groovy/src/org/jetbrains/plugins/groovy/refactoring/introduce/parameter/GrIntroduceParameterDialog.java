/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.*;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractUtil;
import org.jetbrains.plugins.groovy.refactoring.extract.ParameterInfo;
import org.jetbrains.plugins.groovy.refactoring.extract.ParameterTablePanel;
import org.jetbrains.plugins.groovy.refactoring.extract.closure.ExtractClosureFromClosureProcessor;
import org.jetbrains.plugins.groovy.refactoring.extract.closure.ExtractClosureFromMethodProcessor;
import org.jetbrains.plugins.groovy.refactoring.extract.closure.ExtractClosureHelperImpl;
import org.jetbrains.plugins.groovy.refactoring.extract.closure.ExtractClosureProcessorBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContextImpl;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.field.GroovyFieldValidator;
import org.jetbrains.plugins.groovy.refactoring.ui.GrMethodSignatureComponent;
import org.jetbrains.plugins.groovy.refactoring.ui.GrTypeComboBox;
import org.jetbrains.plugins.groovy.settings.GroovyApplicationSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import static com.intellij.refactoring.IntroduceParameterRefactoring.*;

public class GrIntroduceParameterDialog extends DialogWrapper implements GrIntroduceDialog<GrIntroduceParameterSettings> {
  private GrTypeComboBox myTypeComboBox;
  private NameSuggestionsField myNameSuggestionsField;
  private JCheckBox myDeclareFinalCheckBox;
  private JCheckBox myDelegateViaOverloadingMethodCheckBox;
  private JBRadioButton myDoNotReplaceRadioButton;
  private JBRadioButton myReplaceFieldsInaccessibleInRadioButton;
  private JBRadioButton myReplaceAllFieldsRadioButton;
  private JPanel myGetterPanel;
  private IntroduceParameterInfo myInfo;
  private TObjectIntHashMap<JCheckBox> toRemoveCBs;

  private GrMethodSignatureComponent mySignature;
  private ParameterTablePanel myTable;
  private JPanel mySignaturePanel;
  private JCheckBox myForceReturnCheckBox;
  private Project myProject;

  private final boolean myCanIntroduceSimpleParameter;

  public GrIntroduceParameterDialog(IntroduceParameterInfo info) {
    super(info.getProject(), true);
    myInfo = info;
    myProject = info.getProject();
    myCanIntroduceSimpleParameter = findExpr() != null || findVar() != null;

    TObjectIntHashMap<GrParameter> parametersToRemove = GroovyIntroduceParameterUtil.findParametersToRemove(info);
    toRemoveCBs = new TObjectIntHashMap<JCheckBox>(parametersToRemove.size());
    for (Object p : parametersToRemove.keys()) {
      JCheckBox cb = new JCheckBox(GroovyRefactoringBundle.message("remove.parameter.0.no.longer.used", ((GrParameter)p).getName()));
      toRemoveCBs.put(cb, parametersToRemove.get((GrParameter)p));
      cb.setSelected(true);
    }

    init();
  }

  @Override
  protected void init() {
    super.init();

    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();

    initReplaceFieldsWithGetters(settings);

    myDeclareFinalCheckBox.setSelected(hasFinalModifier());
    myDelegateViaOverloadingMethodCheckBox.setVisible(myInfo.getToSearchFor() != null);

    setTitle(RefactoringBundle.message("introduce.parameter.title"));

    myTable.init(myInfo);

    final GrParameter[] parameters = myInfo.getToReplaceIn().getParameters();
    toRemoveCBs.forEachEntry(new TObjectIntProcedure<JCheckBox>() {
      @Override
      public boolean execute(JCheckBox checkbox, int index) {
        checkbox.setSelected(true);

        final GrParameter param = parameters[index];
        final ParameterInfo pinfo = findParamByOldName(param.getName());
        if (pinfo != null) {
          pinfo.setPassAsParameter(false);
        }
        return true;
      }
    });

    updateSignature();

    if (myCanIntroduceSimpleParameter) {
      mySignaturePanel.setVisible(false);

      //action to hide signature panel if we have variants to introduce simple parameter
      myTypeComboBox.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          mySignaturePanel.setVisible(myTypeComboBox.isClosureSelected());
          pack();
        }
      });
    }

    final PsiType closureReturnType = inferClosureReturnType();
    if (closureReturnType == PsiType.VOID) {
      myForceReturnCheckBox.setEnabled(false);
      myForceReturnCheckBox.setSelected(false);
    }
    else {
      myForceReturnCheckBox.setSelected(isForceReturn());
    }

    if (myInfo.getToReplaceIn() instanceof GrClosableBlock) {
      myDelegateViaOverloadingMethodCheckBox.setEnabled(false);
      myDelegateViaOverloadingMethodCheckBox.setToolTipText("Delegating is not allowed in closure context");
    }


    pack();
  }

  private static boolean isForceReturn() {
    return GroovyApplicationSettings.getInstance().FORCE_RETURN;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel north = new JPanel();
    north.setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true));

    final JPanel namePanel = createNamePanel();
    namePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    north.add(namePanel);

    createCheckBoxes(north);

    myGetterPanel = createFieldPanel();
    myGetterPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    north.add(myGetterPanel);

    final JPanel root = new JPanel(new BorderLayout());
    mySignaturePanel = createSignaturePanel();
    root.add(mySignaturePanel, BorderLayout.CENTER);
    root.add(north, BorderLayout.NORTH);

    return root;
  }

  private JPanel createSignaturePanel() {
    mySignature = new GrMethodSignatureComponent("", myProject);
    myTable = new ParameterTablePanel() {
      @Override
      protected void updateSignature() {
        GrIntroduceParameterDialog.this.updateSignature();
      }

      @Override
      protected void doEnterAction() {
        clickDefaultButton();
      }

      @Override
      protected void doCancelAction() {
        GrIntroduceParameterDialog.this.doCancelAction();
      }
    };

    mySignature.setBorder(IdeBorderFactory.createTitledBorder(GroovyRefactoringBundle.message("signature.preview.border.title"), false));

    Splitter splitter = new Splitter(true);

    splitter.setFirstComponent(myTable);
    splitter.setSecondComponent(mySignature);

    mySignature.setPreferredSize(new Dimension(500, 100));
    mySignature.setSize(new Dimension(500, 100));

    splitter.setShowDividerIcon(false);

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(splitter, BorderLayout.CENTER);
    myForceReturnCheckBox = new JCheckBox(UIUtil.replaceMnemonicAmpersand("Use e&xplicit return statement"));
    panel.add(myForceReturnCheckBox, BorderLayout.NORTH);

    return panel;
  }

  private JPanel createFieldPanel() {
    myDoNotReplaceRadioButton = new JBRadioButton(UIUtil.replaceMnemonicAmpersand("Do n&ot replace"));
    myReplaceFieldsInaccessibleInRadioButton = new JBRadioButton(UIUtil.replaceMnemonicAmpersand("Replace fields &inaccessible in usage context"));
    myReplaceAllFieldsRadioButton = new JBRadioButton(UIUtil.replaceMnemonicAmpersand("&Replace all fields"));

    myDoNotReplaceRadioButton.setFocusable(false);
    myReplaceFieldsInaccessibleInRadioButton.setFocusable(false);
    myReplaceAllFieldsRadioButton.setFocusable(false);

    final ButtonGroup group = new ButtonGroup();
    group.add(myDoNotReplaceRadioButton);
    group.add(myReplaceFieldsInaccessibleInRadioButton);
    group.add(myReplaceAllFieldsRadioButton);

    final JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(myDoNotReplaceRadioButton);
    panel.add(myReplaceFieldsInaccessibleInRadioButton);
    panel.add(myReplaceAllFieldsRadioButton);

    panel.setBorder(IdeBorderFactory.createTitledBorder("Replace fields used in expression with their getters", true));
    return panel;
  }

  private JPanel createNamePanel() {
    final GridBag c = new GridBag().setDefaultAnchor(GridBagConstraints.WEST).setDefaultInsets(1, 1, 1, 1);
    final JPanel namePanel = new JPanel(new GridBagLayout());
    final JLabel nameLabel = new JLabel(UIUtil.replaceMnemonicAmpersand("&Name:"));
    c.nextLine().next().weightx(0).fillCellNone();
    namePanel.add(nameLabel, c);

    myNameSuggestionsField = createNameField(findVar(), findExpr());
    c.next().weightx(1).fillCellHorizontally();
    namePanel.add(myNameSuggestionsField, c);
    nameLabel.setLabelFor(myNameSuggestionsField);

    final JLabel typeLabel = new JLabel(UIUtil.replaceMnemonicAmpersand("&Type:"));
    c.nextLine().next().weightx(0).fillCellNone();
    namePanel.add(typeLabel, c);

    myTypeComboBox = createTypeComboBox(findVar(), findExpr());
    c.next().weightx(1).fillCellHorizontally();
    namePanel.add(myTypeComboBox, c);
    typeLabel.setLabelFor(myTypeComboBox);

    return namePanel;
  }

  private void createCheckBoxes(JPanel panel) {
    myDeclareFinalCheckBox = new JCheckBox(UIUtil.replaceMnemonicAmpersand("Declare &final"));
    myDeclareFinalCheckBox.setFocusable(false);
    panel.add(myDeclareFinalCheckBox);

    myDelegateViaOverloadingMethodCheckBox = new JCheckBox(UIUtil.replaceMnemonicAmpersand("De&legate via overloading method"));
    myDelegateViaOverloadingMethodCheckBox.setFocusable(false);
    panel.add(myDelegateViaOverloadingMethodCheckBox);

    for (Object o : toRemoveCBs.keys()) {
      final JCheckBox cb = (JCheckBox)o;
      cb.setFocusable(false);
      panel.add(cb);
    }
  }

  private GrTypeComboBox createTypeComboBox(GrVariable var, GrExpression expr) {
    GrTypeComboBox box;
    if (var != null) {
      box = GrTypeComboBox.createTypeComboBoxWithDefType(var.getDeclaredType());
    }
    else if (expr != null) {
      box = GrTypeComboBox.createTypeComboBoxFromExpression(expr);
    }
    else {
      box = GrTypeComboBox.createEmptyTypeComboBox();
    }

    box.addClosureTypesFrom(inferClosureReturnType(), myInfo.getContext());
    if (expr == null && var == null) {
      box.setSelectedIndex(box.getItemCount() - 1);
    }
    return box;
  }

  @Nullable
  private PsiType inferClosureReturnType() {
    final ExtractClosureHelperImpl mockHelper =
      new ExtractClosureHelperImpl(myInfo, "__test___n_", false, new TIntArrayList(), false, 0, false, false);
    final PsiType returnType;
    final AccessToken token = WriteAction.start();
    try {
      returnType = ExtractClosureProcessorBase.generateClosure(mockHelper).getReturnType();
    }
    finally {
      token.finish();
    }
    return returnType;
  }

  private NameSuggestionsField createNameField(GrVariable var, GrExpression expr) {
    String[] possibleNames;
    if (expr != null) {
      final GrIntroduceContext
        introduceContext = new GrIntroduceContextImpl(myProject, null, expr, var, PsiElement.EMPTY_ARRAY, myInfo.getToReplaceIn());
      final GroovyFieldValidator validator = new GroovyFieldValidator(introduceContext);
      possibleNames = GroovyNameSuggestionUtil.suggestVariableNames(expr, validator, true);
    }
    else if (var != null) {
      final GrIntroduceContext introduceContext =
        new GrIntroduceContextImpl(myProject, null, expr, var, PsiElement.EMPTY_ARRAY, myInfo.getToReplaceIn());
      final GroovyFieldValidator validator = new GroovyFieldValidator(introduceContext);
      possibleNames = GroovyNameSuggestionUtil.suggestVariableNameByType(var.getType(), validator);
    }
    else {
      possibleNames = new String[]{"closure"};
    }

    if (var != null) {
      String[] arr = new String[possibleNames.length + 1];
      arr[0] = var.getName();
      System.arraycopy(possibleNames, 0, arr, 1, possibleNames.length);
      possibleNames = arr;
    }
    return new NameSuggestionsField(possibleNames, myProject, GroovyFileType.GROOVY_FILE_TYPE);
  }

  private void initReplaceFieldsWithGetters(JavaRefactoringSettings settings) {
    final PsiField[] usedFields = GroovyIntroduceParameterUtil.findUsedFieldsWithGetters(myInfo.getStatements(), getContainingClass());
    myGetterPanel.setVisible(usedFields.length > 0);
    switch (settings.INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS) {
      case REPLACE_FIELDS_WITH_GETTERS_ALL:
        myReplaceAllFieldsRadioButton.setSelected(true);
        break;
      case REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE:
        myReplaceFieldsInaccessibleInRadioButton.setSelected(true);
        break;
      case REPLACE_FIELDS_WITH_GETTERS_NONE:
        myDoNotReplaceRadioButton.setSelected(true);
        break;
    }
  }

  private void updateSignature() {
    StringBuilder b = new StringBuilder();
    b.append("{ ");
    String[] params = ExtractUtil.getParameterString(myInfo, false);
    for (int i = 0; i < params.length; i++) {
      if (i > 0) {
        b.append("  ");
      }
      b.append(params[i]);
      b.append('\n');
    }
    b.append(" ->\n}");
    mySignature.setSignature(b.toString());
  }

  @Override
  protected ValidationInfo doValidate() {
    final String text = myNameSuggestionsField.getEnteredName();
    if (!GroovyNamesUtil.isIdentifier(text)) {
      return new ValidationInfo(GroovyRefactoringBundle.message("name.is.wrong", text), myNameSuggestionsField);
    }

    if (myTypeComboBox.isClosureSelected()) {
      final Ref<ValidationInfo> info = new Ref<ValidationInfo>();
      toRemoveCBs.forEachEntry(new TObjectIntProcedure<JCheckBox>() {
        @Override
        public boolean execute(JCheckBox checkbox, int index) {
          if (!checkbox.isSelected()) return true;

          final GrParameter param = myInfo.getToReplaceIn().getParameters()[index];
          final ParameterInfo pinfo = findParamByOldName(param.getName());
          if (pinfo == null || !pinfo.passAsParameter()) return true;

          final String message = GroovyRefactoringBundle
            .message("you.cannot.pass.as.parameter.0.because.you.remove.1.from.base.method", pinfo.getName(), param.getName());
          info.set(new ValidationInfo(message));
          return false;
        }
      });
      if (info.get() != null) {
        return info.get();
      }
    }

    return null;
  }

  @Nullable
  private ParameterInfo findParamByOldName(String name) {
    for (ParameterInfo info : myInfo.getParameterInfos()) {
      if (name.equals(info.getOldName())) return info;
    }
    return null;
  }

  @Nullable
  private PsiClass getContainingClass() {
    final GrParametersOwner toReplaceIn = myInfo.getToReplaceIn();
    if (toReplaceIn instanceof GrMethod) {
      return ((GrMethod)toReplaceIn).getContainingClass();
    }
    else {
      return PsiTreeUtil.getContextOfType(toReplaceIn, PsiClass.class);
    }
  }

  private boolean hasFinalModifier() {
    final Boolean createFinals = JavaRefactoringSettings.getInstance().INTRODUCE_PARAMETER_CREATE_FINALS;
    return createFinals == null ? CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS : createFinals.booleanValue();
  }

  @Override
  public void doOKAction() {
    saveSettings();

    super.doOKAction();

    final GrParametersOwner toReplaceIn = myInfo.getToReplaceIn();

    final GrExpression expr = findExpr();
    final GrVariable var = findVar();

    if (myTypeComboBox.isClosureSelected() || expr == null && var == null) {
      GrIntroduceParameterSettings settings = new ExtractClosureHelperImpl(myInfo,
                                                                           myNameSuggestionsField.getEnteredName(),
                                                                           myDeclareFinalCheckBox.isSelected(),
                                                                           getParametersToRemove(),
                                                                           myDelegateViaOverloadingMethodCheckBox.isSelected(),
                                                                           getReplaceFieldsWithGetter(),
                                                                           myForceReturnCheckBox.isSelected(),
                                                                           myTypeComboBox.getSelectedType() == null);
      if (toReplaceIn instanceof GrMethod) {
        invokeRefactoring(new ExtractClosureFromMethodProcessor(settings));
      }
      else {
        invokeRefactoring(new ExtractClosureFromClosureProcessor(settings));
      }
    }
    else {

      GrIntroduceParameterSettings settings = new GrIntroduceExpressionSettingsImpl(myInfo,
                                                                                    myNameSuggestionsField.getEnteredName(),
                                                                                    myDeclareFinalCheckBox.isSelected(),
                                                                                    getParametersToRemove(),
                                                                                    myDelegateViaOverloadingMethodCheckBox.isSelected(),
                                                                                    getReplaceFieldsWithGetter(),
                                                                                    expr,
                                                                                    var,
                                                                                    myTypeComboBox.getSelectedType(),
                                                                                    myForceReturnCheckBox.isSelected());
      if (toReplaceIn instanceof GrMethod) {
        invokeRefactoring(new GrIntroduceParameterProcessor(settings));
      }
      else {
        invokeRefactoring(new GrIntroduceClosureParameterProcessor(settings));
      }
    }
  }

  private void saveSettings() {
    final JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    settings.INTRODUCE_PARAMETER_CREATE_FINALS = myDeclareFinalCheckBox.isSelected();
    if (myGetterPanel.isVisible()) {
      settings.INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS = getReplaceFieldsWithGetter();
    }
    if (myForceReturnCheckBox.isEnabled() && mySignaturePanel.isVisible()) {
      GroovyApplicationSettings.getInstance().FORCE_RETURN = myForceReturnCheckBox.isSelected();
    }
  }

  protected void invokeRefactoring(BaseRefactoringProcessor processor) {
    final Runnable prepareSuccessfulCallback = new Runnable() {
      public void run() {
        close(DialogWrapper.OK_EXIT_CODE);
      }
    };
    processor.setPrepareSuccessfulSwingThreadCallback(prepareSuccessfulCallback);
    processor.setPreviewUsages(false);
    processor.run();
  }

  @Override
  public GrIntroduceParameterSettings getSettings() {
    return null;
  }

  private int getReplaceFieldsWithGetter() {
    if (myDoNotReplaceRadioButton.isSelected()) return REPLACE_FIELDS_WITH_GETTERS_NONE;
    if (myReplaceFieldsInaccessibleInRadioButton.isSelected()) return REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE;
    if (myReplaceAllFieldsRadioButton.isSelected()) return REPLACE_FIELDS_WITH_GETTERS_ALL;
    throw new GrRefactoringError("no check box selected");
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameSuggestionsField;
  }

  @Override
  protected String getHelpId() {
    return HelpID.GROOVY_INTRODUCE_PARAMETER;
  }

  private TIntArrayList getParametersToRemove() {
    TIntArrayList list = new TIntArrayList();
    for (Object o : toRemoveCBs.keys()) {
      if (((JCheckBox)o).isSelected()) {
        list.add(toRemoveCBs.get((JCheckBox)o));
      }
    }
    return list;
  }


  @Nullable
  private GrVariable findVar() {
    final GrStatement[] statements = myInfo.getStatements();
    if (statements.length > 1) return null;
    return GrIntroduceHandlerBase.findVariable(statements[0]);
  }

  @Nullable
  private GrExpression findExpr() {
    final GrStatement[] statements = myInfo.getStatements();
    if (statements.length > 1) return null;
    return GrIntroduceHandlerBase.findExpression(statements[0]);
  }
}

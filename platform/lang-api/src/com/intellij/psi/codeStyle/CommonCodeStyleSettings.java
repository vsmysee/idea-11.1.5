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
package com.intellij.psi.codeStyle;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.*;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.intellij.lang.annotations.MagicConstant;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;

/**
 * Common code style settings can be used by several programming languages. Each language may have its own
 * instance of <code>CommonCodeStyleSettings</code>.
 *
 * @author Rustam Vishnyakov
 */
public class CommonCodeStyleSettings {

  private Language myLanguage;
  private CodeStyleSettings myRootSettings;
  private IndentOptions myIndentOptions;
  private FileType myFileType;

  private final static String INDENT_OPTIONS_TAG = "indentOptions";

  public CommonCodeStyleSettings(Language language, FileType fileType) {
    myLanguage = language;
    myFileType = fileType;
  }

  public CommonCodeStyleSettings(Language language) {
    myLanguage = language;
    if (language != null) {
      myFileType = language.getAssociatedFileType();
    }
  }

  void setRootSettings(CodeStyleSettings rootSettings) {
    myRootSettings = rootSettings;
  }

  public Language getLanguage() {
    return myLanguage;
  }

  void importOldIndentOptions(@NotNull CodeStyleSettings rootSettings) {
    if (myFileType != null && myIndentOptions != null) {
      if (getFileTypeIndentOptionsProvider() == null) {
        IndentOptions fileTypeIdentOptions = rootSettings.getAdditionalIndentOptions(myFileType);
        if (fileTypeIdentOptions != null) {
          myIndentOptions.copyFrom(fileTypeIdentOptions);
          rootSettings.unregisterAdditionalIndentOptions(myFileType);
        }
        else if (rootSettings.USE_SAME_INDENTS && !rootSettings.IGNORE_SAME_INDENTS_FOR_LANGUAGES) {
          myIndentOptions.copyFrom(rootSettings.OTHER_INDENT_OPTIONS);
        }
      }
    }
  }

  @NotNull
  public IndentOptions initIndentOptions() {
    myIndentOptions = new IndentOptions();
    return myIndentOptions;
  }

  @Nullable
  private FileTypeIndentOptionsProvider getFileTypeIndentOptionsProvider() {
    final FileTypeIndentOptionsProvider[] providers = Extensions.getExtensions(FileTypeIndentOptionsProvider.EP_NAME);
    for (FileTypeIndentOptionsProvider provider : providers) {
      if (provider.getFileType().equals(myFileType)) {
        return provider;
      }
    }
    return null;
  }


  @Nullable
  public FileType getFileType() {
    return myFileType;
  }

  @NotNull
  public CodeStyleSettings getRootSettings() {
    return myRootSettings;
  }

  @Nullable
  public IndentOptions getIndentOptions() {
    return myIndentOptions;
  }

  public CommonCodeStyleSettings clone(CodeStyleSettings rootSettings) {
    assert rootSettings != null;
    CommonCodeStyleSettings commonSettings = new CommonCodeStyleSettings(myLanguage, getFileType());
    copyPublicFields(this, commonSettings);
    commonSettings.setRootSettings(rootSettings);
    if (myIndentOptions != null) {
      IndentOptions targetIndentOptions = commonSettings.initIndentOptions();
      targetIndentOptions.copyFrom(myIndentOptions);
    }
    return commonSettings;
  }

  protected static void copyPublicFields(Object from, Object to) {
    assert from != to;
    copyFields(to.getClass().getFields(), from, to);
  }

  void copyNonDefaultValuesFrom(CommonCodeStyleSettings from) {
    CommonCodeStyleSettings defaultSettings = new CommonCodeStyleSettings(null);
    PARENT_SETTINGS_INSTALLED =
      copyFields(this.getClass().getFields(), from, this, new SupportedFieldsDiffFilter(from, getSupportedFields(), defaultSettings));
  }

  private static void copyFields(Field[] fields, Object from, Object to) {
    copyFields(fields, from, to, null);
  }

  private static boolean copyFields(Field[] fields, Object from, Object to, @Nullable DifferenceFilter diffFilter) {
    boolean valuesChanged = false;
    for (Field field : fields) {
      if (isPublic(field) && !isFinal(field)) {
        try {
          if (diffFilter == null || diffFilter.isAccept(field)) {
            copyFieldValue(from, to, field);
            valuesChanged = true;
          }
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
    return valuesChanged;
  }

  private static void copyFieldValue(final Object from, Object to, final Field field)
    throws IllegalAccessException {
    Class<?> fieldType = field.getType();
    if (fieldType.isPrimitive()) {
      field.set(to, field.get(from));
    }
    else if (fieldType.equals(String.class)) {
      field.set(to, field.get(from));
    }
    else {
      throw new RuntimeException("Field not copied " + field.getName());
    }
  }

  private static boolean isPublic(final Field field) {
    return (field.getModifiers() & Modifier.PUBLIC) != 0;
  }

  private static boolean isFinal(final Field field) {
    return (field.getModifiers() & Modifier.FINAL) != 0;
  }

  @Nullable
  private CommonCodeStyleSettings getDefaultSettings() {
    return LanguageCodeStyleSettingsProvider.getDefaultCommonSettings(myLanguage);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    if (myIndentOptions != null) {
      Element indentOptionsElement = element.getChild(INDENT_OPTIONS_TAG);
      if (indentOptionsElement != null) {
        myIndentOptions.deserialize(indentOptionsElement);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    CommonCodeStyleSettings defaultSettings = getDefaultSettings();
    Set<String> supportedFields = getSupportedFields();
    if (supportedFields != null) {
      supportedFields.add("PARENT_SETTINGS_INSTALLED");
    }
    DefaultJDOMExternalizer.writeExternal(this, element, new SupportedFieldsDiffFilter(this, supportedFields, defaultSettings));
    if (myIndentOptions != null) {
      IndentOptions defaultIndentOptions = defaultSettings != null ? defaultSettings.getIndentOptions() : null;
      Element indentOptionsElement = new Element(INDENT_OPTIONS_TAG);
      myIndentOptions.serialize(indentOptionsElement, defaultIndentOptions);
      if (indentOptionsElement.getChildren().size() > 0) {
        element.addContent(indentOptionsElement);
      }
    }
  }

  @Nullable
  private Set<String> getSupportedFields() {
    final LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(myLanguage);
    return provider == null ? null : provider.getSupportedFields();
  }
  
  private static class SupportedFieldsDiffFilter extends DifferenceFilter<CommonCodeStyleSettings> {
    
    private Set<String> mySupportedFieldNames;

    public SupportedFieldsDiffFilter(final CommonCodeStyleSettings object,
                                     Set<String> supportedFiledNames,
                                     final CommonCodeStyleSettings parentObject) {
      super(object, parentObject);
      mySupportedFieldNames = supportedFiledNames;
    }

    @Override
    public boolean isAccept(Field field) {
      if (mySupportedFieldNames == null ||
          mySupportedFieldNames.contains(field.getName())) {
        return super.isAccept(field);
      }
      return false;
    }
  }

//----------------- GENERAL --------------------

  public boolean LINE_COMMENT_AT_FIRST_COLUMN = true;
  public boolean BLOCK_COMMENT_AT_FIRST_COLUMN = true;

  public boolean KEEP_LINE_BREAKS = true;

  /**
   * Controls END_OF_LINE_COMMENT's and C_STYLE_COMMENT's
   */
  public boolean KEEP_FIRST_COLUMN_COMMENT = true;
  public boolean INSERT_FIRST_SPACE_IN_LINE = true;

  /**
   * Keep "if (..) ...;" (also while, for)
   * Does not control "if (..) { .. }"
   */
  public boolean KEEP_CONTROL_STATEMENT_IN_ONE_LINE = true;

//----------------- BLANK LINES --------------------

  /**
   * Keep up to this amount of blank lines between declarations
   */
  public int KEEP_BLANK_LINES_IN_DECLARATIONS = 2;

  /**
   * Keep up to this amount of blank lines in code
   */
  public int KEEP_BLANK_LINES_IN_CODE = 2;

  public int KEEP_BLANK_LINES_BEFORE_RBRACE = 2;

  public int BLANK_LINES_BEFORE_PACKAGE = 0;
  public int BLANK_LINES_AFTER_PACKAGE = 1;
  public int BLANK_LINES_BEFORE_IMPORTS = 1;
  public int BLANK_LINES_AFTER_IMPORTS = 1;

  public int BLANK_LINES_AROUND_CLASS = 1;
  public int BLANK_LINES_AROUND_FIELD = 0;
  public int BLANK_LINES_AROUND_METHOD = 1;
  public int BLANK_LINES_BEFORE_METHOD_BODY = 0;

  public int BLANK_LINES_AROUND_FIELD_IN_INTERFACE = 0;
  public int BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 1;


  public int BLANK_LINES_AFTER_CLASS_HEADER = 0;
  public int BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER = 0;
  //public int BLANK_LINES_BETWEEN_CASE_BLOCKS;


//----------------- BRACES & INDENTS --------------------

  /**
   * <PRE>
   * 1.
   * if (..) {
   * body;
   * }
   * 2.
   * if (..)
   * {
   * body;
   * }
   * 3.
   * if (..)
   * {
   * body;
   * }
   * 4.
   * if (..)
   * {
   * body;
   * }
   * 5.
   * if (long-condition-1 &&
   * long-condition-2)
   * {
   * body;
   * }
   * if (short-condition) {
   * body;
   * }
   * </PRE>
   */

  public static final int END_OF_LINE = 1;
  public static final int NEXT_LINE = 2;
  public static final int NEXT_LINE_SHIFTED = 3;
  public static final int NEXT_LINE_SHIFTED2 = 4;
  public static final int NEXT_LINE_IF_WRAPPED = 5;

  @MagicConstant(intValues = {END_OF_LINE, NEXT_LINE, NEXT_LINE_SHIFTED, NEXT_LINE_SHIFTED2, NEXT_LINE_IF_WRAPPED})
  public @interface BraceStyleConstant {}

  @BraceStyleConstant public int BRACE_STYLE = END_OF_LINE;
  @BraceStyleConstant public int CLASS_BRACE_STYLE = END_OF_LINE;
  @BraceStyleConstant public int METHOD_BRACE_STYLE = END_OF_LINE;

  /**
   * Defines if 'flying geese' style should be used for curly braces formatting, e.g. if we want to format code like
   * <p/>
   * <pre>
   *     class Test {
   *         {
   *             System.out.println();
   *         }
   *     }
   * </pre>
   * to
   * <pre>
   *     class Test { {
   *         System.out.println();
   *     } }
   * </pre>
   */
  public boolean USE_FLYING_GEESE_BRACES = false;

  /**
   * Defines number of white spaces between curly braces in case of {@link #USE_FLYING_GEESE_BRACES 'flying geese'} style usage.
   */
  public int FLYING_GEESE_BRACES_GAP = 1;

  public boolean DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = false;

  /**
   * <PRE>
   * "}
   * else"
   * or
   * "} else"
   * </PRE>
   */
  public boolean ELSE_ON_NEW_LINE = false;

  /**
   * <PRE>
   * "}
   * while"
   * or
   * "} while"
   * </PRE>
   */
  public boolean WHILE_ON_NEW_LINE = false;

  /**
   * <PRE>
   * "}
   * catch"
   * or
   * "} catch"
   * </PRE>
   */
  public boolean CATCH_ON_NEW_LINE = false;

  /**
   * <PRE>
   * "}
   * finally"
   * or
   * "} finally"
   * </PRE>
   */
  public boolean FINALLY_ON_NEW_LINE = false;

  public boolean INDENT_CASE_FROM_SWITCH = true;

  public boolean SPECIAL_ELSE_IF_TREATMENT = true;

  /**
   * Indicates if long sequence of chained method calls should be aligned.
   * <p/>
   * E.g. if statement like <code>'foo.bar().bar().bar();'</code> should be reformatted to the one below if,
   * say, last <code>'bar()'</code> call exceeds right margin. The code looks as follows after reformatting
   * if this property is <code>true</code>:
   * <p/>
   * <pre>
   *     foo.bar().bar()
   *        .bar();
   * </pre>
   */
  public boolean ALIGN_MULTILINE_CHAINED_METHODS = false;
  public boolean ALIGN_MULTILINE_PARAMETERS = true;
  public boolean ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false;
  public boolean ALIGN_MULTILINE_RESOURCES = true;
  public boolean ALIGN_MULTILINE_FOR = true;
  public boolean INDENT_WHEN_CASES = true;

  public boolean ALIGN_MULTILINE_BINARY_OPERATION = false;
  public boolean ALIGN_MULTILINE_ASSIGNMENT = false;
  public boolean ALIGN_MULTILINE_TERNARY_OPERATION = false;
  public boolean ALIGN_MULTILINE_THROWS_LIST = false;
  public boolean ALIGN_THROWS_KEYWORD = false;

  public boolean ALIGN_MULTILINE_EXTENDS_LIST = false;
  public boolean ALIGN_MULTILINE_METHOD_BRACKETS = false;
  public boolean ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = false;
  public boolean ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = false;

//----------------- Group alignments ---------------

  /**
   * Specifies if subsequent fields/variables declarations and initialisations should be aligned in columns like below:
   * int start = 1;
   * int end   = 10;
   */
  public boolean ALIGN_GROUP_FIELD_DECLARATIONS = false;

//----------------- SPACES --------------------

  /**
   * Controls =, +=, -=, etc
   */
  public boolean SPACE_AROUND_ASSIGNMENT_OPERATORS = true;

  /**
   * Controls &&, ||
   */
  public boolean SPACE_AROUND_LOGICAL_OPERATORS = true;

  /**
   * Controls ==, !=
   */
  public boolean SPACE_AROUND_EQUALITY_OPERATORS = true;

  /**
   * Controls <, >, <=, >=
   */
  public boolean SPACE_AROUND_RELATIONAL_OPERATORS = true;

  /**
   * Controls &, |, ^
   */
  public boolean SPACE_AROUND_BITWISE_OPERATORS = true;

  /**
   * Controls +, -
   */
  public boolean SPACE_AROUND_ADDITIVE_OPERATORS = true;

  /**
   * Controls *, /, %
   */
  public boolean SPACE_AROUND_MULTIPLICATIVE_OPERATORS = true;

  /**
   * Controls <<. >>, >>>
   */
  public boolean SPACE_AROUND_SHIFT_OPERATORS = true;

  public boolean SPACE_AROUND_UNARY_OPERATOR = false;

  public boolean SPACE_AFTER_COMMA = true;
  public boolean SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS = true;
  public boolean SPACE_BEFORE_COMMA = false;
  public boolean SPACE_AFTER_SEMICOLON = true; // in for-statement
  public boolean SPACE_BEFORE_SEMICOLON = false; // in for-statement

  /**
   * "( expr )"
   * or
   * "(expr)"
   */
  public boolean SPACE_WITHIN_PARENTHESES = false;

  /**
   * "f( expr )"
   * or
   * "f(expr)"
   */
  public boolean SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;

  /**
   * "f( )"
   * or
   * "f()"
   */
  public boolean SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = false;

  /**
   * "void f( int param )"
   * or
   * "void f(int param)"
   */
  public boolean SPACE_WITHIN_METHOD_PARENTHESES = false;

  /**
   * "void f( )"
   * or
   * "void f()"
   */
  public boolean SPACE_WITHIN_EMPTY_METHOD_PARENTHESES = false;

  /**
   * "if( expr )"
   * or
   * "if(expr)"
   */
  public boolean SPACE_WITHIN_IF_PARENTHESES = false;

  /**
   * "while( expr )"
   * or
   * "while(expr)"
   */
  public boolean SPACE_WITHIN_WHILE_PARENTHESES = false;

  /**
   * "for( int i = 0; i < 10; i++ )"
   * or
   * "for(int i = 0; i < 10; i++)"
   */
  public boolean SPACE_WITHIN_FOR_PARENTHESES = false;

  /**
   * "try( Resource r = r() )"
   * or
   * "catch(Resource r = r())"
   */
  public boolean SPACE_WITHIN_TRY_PARENTHESES = false;

  /**
   * "catch( Exception e )"
   * or
   * "catch(Exception e)"
   */
  public boolean SPACE_WITHIN_CATCH_PARENTHESES = false;

  /**
   * "switch( expr )"
   * or
   * "switch(expr)"
   */
  public boolean SPACE_WITHIN_SWITCH_PARENTHESES = false;

  /**
   * "synchronized( expr )"
   * or
   * "synchronized(expr)"
   */
  public boolean SPACE_WITHIN_SYNCHRONIZED_PARENTHESES = false;

  /**
   * "( Type )expr"
   * or
   * "(Type)expr"
   */
  public boolean SPACE_WITHIN_CAST_PARENTHESES = false;

  /**
   * "[ expr ]"
   * or
   * "[expr]"
   */
  public boolean SPACE_WITHIN_BRACKETS = false;

  /**
   * void foo(){ { return; } }
   * or
   * void foo(){{return;}}
   */
  public boolean SPACE_WITHIN_BRACES = false;

  /**
   * "int X[] { 1, 3, 5 }"
   * or
   * "int X[] {1, 3, 5}"
   */
  public boolean SPACE_WITHIN_ARRAY_INITIALIZER_BRACES = false;

  public boolean SPACE_AFTER_TYPE_CAST = true;

  /**
   * "f (x)"
   * or
   * "f(x)"
   */
  public boolean SPACE_BEFORE_METHOD_CALL_PARENTHESES = false;

  /**
   * "void f (int param)"
   * or
   * "void f(int param)"
   */
  public boolean SPACE_BEFORE_METHOD_PARENTHESES = false;

  /**
   * "if (...)"
   * or
   * "if(...)"
   */
  public boolean SPACE_BEFORE_IF_PARENTHESES = true;

  /**
   * "while (...)"
   * or
   * "while(...)"
   */
  public boolean SPACE_BEFORE_WHILE_PARENTHESES = true;

  /**
   * "for (...)"
   * or
   * "for(...)"
   */
  public boolean SPACE_BEFORE_FOR_PARENTHESES = true;

  /**
   * "try (...)"
   * or
   * "try(...)"
   */
  public boolean SPACE_BEFORE_TRY_PARENTHESES = true;

  /**
   * "catch (...)"
   * or
   * "catch(...)"
   */
  public boolean SPACE_BEFORE_CATCH_PARENTHESES = true;

  /**
   * "switch (...)"
   * or
   * "switch(...)"
   */
  public boolean SPACE_BEFORE_SWITCH_PARENTHESES = true;

  /**
   * "synchronized (...)"
   * or
   * "synchronized(...)"
   */
  public boolean SPACE_BEFORE_SYNCHRONIZED_PARENTHESES = true;

  /**
   * "class A {"
   * or
   * "class A{"
   */
  public boolean SPACE_BEFORE_CLASS_LBRACE = true;

  /**
   * "void f() {"
   * or
   * "void f(){"
   */
  public boolean SPACE_BEFORE_METHOD_LBRACE = true;

  /**
   * "if (...) {"
   * or
   * "if (...){"
   */
  public boolean SPACE_BEFORE_IF_LBRACE = true;

  /**
   * "else {"
   * or
   * "else{"
   */
  public boolean SPACE_BEFORE_ELSE_LBRACE = true;

  /**
   * "while (...) {"
   * or
   * "while (...){"
   */
  public boolean SPACE_BEFORE_WHILE_LBRACE = true;

  /**
   * "for (...) {"
   * or
   * "for (...){"
   */
  public boolean SPACE_BEFORE_FOR_LBRACE = true;

  /**
   * "do {"
   * or
   * "do{"
   */
  public boolean SPACE_BEFORE_DO_LBRACE = true;

  /**
   * "switch (...) {"
   * or
   * "switch (...){"
   */
  public boolean SPACE_BEFORE_SWITCH_LBRACE = true;

  /**
   * "try {"
   * or
   * "try{"
   */
  public boolean SPACE_BEFORE_TRY_LBRACE = true;

  /**
   * "catch (...) {"
   * or
   * "catch (...){"
   */
  public boolean SPACE_BEFORE_CATCH_LBRACE = true;

  /**
   * "finally {"
   * or
   * "finally{"
   */
  public boolean SPACE_BEFORE_FINALLY_LBRACE = true;

  /**
   * "synchronized (...) {"
   * or
   * "synchronized (...){"
   */
  public boolean SPACE_BEFORE_SYNCHRONIZED_LBRACE = true;

  /**
   * "new int[] {"
   * or
   * "new int[]{"
   */
  public boolean SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = false;

  /**
   * '@SuppressWarnings({"unchecked"})
   * or
   * '@SuppressWarnings( {"unchecked"})
   */
  public boolean SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE = false;

  public boolean SPACE_BEFORE_ELSE_KEYWORD = true;
  public boolean SPACE_BEFORE_WHILE_KEYWORD = true;
  public boolean SPACE_BEFORE_CATCH_KEYWORD = true;
  public boolean SPACE_BEFORE_FINALLY_KEYWORD = true;

  public boolean SPACE_BEFORE_QUEST = true;
  public boolean SPACE_AFTER_QUEST = true;
  public boolean SPACE_BEFORE_COLON = true;
  public boolean SPACE_AFTER_COLON = true;
  public boolean SPACE_BEFORE_TYPE_PARAMETER_LIST = false;

  //----------------- WRAPPING ---------------------------

  public static final int DO_NOT_WRAP = 0x00;
  public static final int WRAP_AS_NEEDED = 0x01;
  public static final int WRAP_ALWAYS = 0x02;
  public static final int WRAP_ON_EVERY_ITEM = 0x04;

  public int CALL_PARAMETERS_WRAP = DO_NOT_WRAP;
  public boolean PREFER_PARAMETERS_WRAP = false;
  public boolean CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
  public boolean CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;

  public int METHOD_PARAMETERS_WRAP = DO_NOT_WRAP;
  public boolean METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
  public boolean METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = false;

  public int RESOURCE_LIST_WRAP = DO_NOT_WRAP;
  public boolean RESOURCE_LIST_LPAREN_ON_NEXT_LINE = false;
  public boolean RESOURCE_LIST_RPAREN_ON_NEXT_LINE = false;

  public int EXTENDS_LIST_WRAP = DO_NOT_WRAP;
  public int THROWS_LIST_WRAP = DO_NOT_WRAP;

  public int EXTENDS_KEYWORD_WRAP = DO_NOT_WRAP;
  public int THROWS_KEYWORD_WRAP = DO_NOT_WRAP;

  public int METHOD_CALL_CHAIN_WRAP = DO_NOT_WRAP;

  public boolean PARENTHESES_EXPRESSION_LPAREN_WRAP = false;
  public boolean PARENTHESES_EXPRESSION_RPAREN_WRAP = false;

  public int BINARY_OPERATION_WRAP = DO_NOT_WRAP;
  public boolean BINARY_OPERATION_SIGN_ON_NEXT_LINE = false;

  public int TERNARY_OPERATION_WRAP = DO_NOT_WRAP;
  public boolean TERNARY_OPERATION_SIGNS_ON_NEXT_LINE = false;

  public boolean MODIFIER_LIST_WRAP = false;

  public boolean KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
  public boolean KEEP_SIMPLE_METHODS_IN_ONE_LINE = false;
  public boolean KEEP_SIMPLE_CLASSES_IN_ONE_LINE = false;
  public boolean KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE = false;

  public int FOR_STATEMENT_WRAP = DO_NOT_WRAP;
  public boolean FOR_STATEMENT_LPAREN_ON_NEXT_LINE = false;
  public boolean FOR_STATEMENT_RPAREN_ON_NEXT_LINE = false;

  public int ARRAY_INITIALIZER_WRAP = DO_NOT_WRAP;
  public boolean ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = false;
  public boolean ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = false;

  public int ASSIGNMENT_WRAP = DO_NOT_WRAP;
  public boolean PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE = false;

  public int LABELED_STATEMENT_WRAP = WRAP_ALWAYS;

  public boolean WRAP_COMMENTS = false;

  public int ASSERT_STATEMENT_WRAP = DO_NOT_WRAP;
  public boolean ASSERT_STATEMENT_COLON_ON_NEXT_LINE = false;

  // BRACE FORCING
  public static final int DO_NOT_FORCE = 0x00;
  public static final int FORCE_BRACES_IF_MULTILINE = 0x01;
  public static final int FORCE_BRACES_ALWAYS = 0x03;

  public int IF_BRACE_FORCE = DO_NOT_FORCE;
  public int DOWHILE_BRACE_FORCE = DO_NOT_FORCE;
  public int WHILE_BRACE_FORCE = DO_NOT_FORCE;
  public int FOR_BRACE_FORCE = DO_NOT_FORCE;

  public boolean WRAP_LONG_LINES = false;

  //-------------- Annotation formatting settings-------------------------------------------

  public int METHOD_ANNOTATION_WRAP = WRAP_ALWAYS;
  public int CLASS_ANNOTATION_WRAP = WRAP_ALWAYS;
  public int FIELD_ANNOTATION_WRAP = WRAP_ALWAYS;
  public int PARAMETER_ANNOTATION_WRAP = DO_NOT_WRAP;
  public int VARIABLE_ANNOTATION_WRAP = DO_NOT_WRAP;

  public boolean SPACE_BEFORE_ANOTATION_PARAMETER_LIST = false;
  public boolean SPACE_WITHIN_ANNOTATION_PARENTHESES = false;

  //----------------------------------------------------------------------------------------


  //-------------------------Enums----------------------------------------------------------
  public int ENUM_CONSTANTS_WRAP = DO_NOT_WRAP;

  //
  // The flag telling that original default settings were overwritten with non-default
  // values from shared code style settings (happens upon the very first initialization).
  //
  public boolean PARENT_SETTINGS_INSTALLED = false;

  //-------------------------Indent options-------------------------------------------------
  public static class IndentOptions implements JDOMExternalizable, Cloneable {
    public int INDENT_SIZE = 4;
    public int CONTINUATION_INDENT_SIZE = 8;
    public int TAB_SIZE = 4;
    public boolean USE_TAB_CHARACTER = false;
    public boolean SMART_TABS = false;
    public int LABEL_INDENT_SIZE = 0;
    public boolean LABEL_INDENT_ABSOLUTE = false;
    public boolean USE_RELATIVE_INDENTS = false;

    public void readExternal(Element element) throws InvalidDataException {
      DefaultJDOMExternalizer.readExternal(this, element);
    }

    public void writeExternal(Element element) throws WriteExternalException {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }

    public void serialize(Element indentOptionsElement, final IndentOptions defaultOptions) {
      XmlSerializer.serializeInto(this, indentOptionsElement, new SkipDefaultValuesSerializationFilters() {
        @Override
        protected void configure(Object o) {
          if (o instanceof IndentOptions && defaultOptions != null) {
            ((IndentOptions)o).copyFrom(defaultOptions);
          }
        }
      });
    }

    public void deserialize(Element indentOptionsElement) {
      XmlSerializer.deserializeInto(this, indentOptionsElement);
    }

    public Object clone() {
      try {
        return super.clone();
      }
      catch (CloneNotSupportedException e) {
        // Cannot happen
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      IndentOptions that = (IndentOptions)o;

      if (CONTINUATION_INDENT_SIZE != that.CONTINUATION_INDENT_SIZE) return false;
      if (INDENT_SIZE != that.INDENT_SIZE) return false;
      if (LABEL_INDENT_ABSOLUTE != that.LABEL_INDENT_ABSOLUTE) return false;
      if (USE_RELATIVE_INDENTS != that.USE_RELATIVE_INDENTS) return false;
      if (LABEL_INDENT_SIZE != that.LABEL_INDENT_SIZE) return false;
      if (SMART_TABS != that.SMART_TABS) return false;
      if (TAB_SIZE != that.TAB_SIZE) return false;
      if (USE_TAB_CHARACTER != that.USE_TAB_CHARACTER) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = INDENT_SIZE;
      result = 31 * result + CONTINUATION_INDENT_SIZE;
      result = 31 * result + TAB_SIZE;
      result = 31 * result + (USE_TAB_CHARACTER ? 1 : 0);
      result = 31 * result + (SMART_TABS ? 1 : 0);
      result = 31 * result + LABEL_INDENT_SIZE;
      result = 31 * result + (LABEL_INDENT_ABSOLUTE ? 1 : 0);
      result = 31 * result + (USE_RELATIVE_INDENTS ? 1 : 0);
      return result;
    }

    public void copyFrom(IndentOptions other) {
      copyPublicFields(other, this);
    }
  }
}

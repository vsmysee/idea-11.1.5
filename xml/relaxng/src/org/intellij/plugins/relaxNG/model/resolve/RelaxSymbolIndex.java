package org.intellij.plugins.relaxNG.model.resolve;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceReader;
import com.intellij.util.xml.NanoXmlUtil;
import org.intellij.plugins.relaxNG.ApplicationLoader;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.model.CommonElement;
import org.intellij.plugins.relaxNG.model.Define;
import org.intellij.plugins.relaxNG.model.Grammar;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 09.06.2010
*/
public class RelaxSymbolIndex extends ScalarIndexExtension<String> {
  @NonNls
  public static final ID<String, Void> NAME = ID.create("RelaxSymbolIndex");

  public static Collection<String> getSymbolNames(Project project) {
    return FileBasedIndex.getInstance().getAllKeys(NAME, project);
  }

  public static NavigationItem[] getSymbolsByName(final String name, Project project, boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project);
    final SymbolCollector processor = new SymbolCollector(name, project, scope);
    FileBasedIndex.getInstance().processValues(NAME, name, null, processor, scope);
    return processor.getResult();
  }

  @NotNull
  @Override
  public ID<String, Void> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return new DataIndexer<String, Void, FileContent>() {
      @Override
      @NotNull
      public Map<String, Void> map(FileContent inputData) {
        final HashMap<String, Void> map = new HashMap<String, Void>();
        if (inputData.getFileType() == XmlFileType.INSTANCE) {
          CharSequence inputDataContentAsText = inputData.getContentAsText();
          if (CharArrayUtil.indexOf(inputDataContentAsText, ApplicationLoader.RNG_NAMESPACE, 0) == -1) return Collections.EMPTY_MAP;
          NanoXmlUtil.parse(new CharSequenceReader(inputDataContentAsText), new NanoXmlUtil.IXMLBuilderAdapter() {
            NanoXmlUtil.IXMLBuilderAdapter attributeHandler;
            int depth;

            @Override
            public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
              if (attributeHandler != null) {
                attributeHandler.addAttribute(key, nsPrefix, nsURI, value, type);
              }
            }

            @Override
            public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
              attributeHandler = null;
              if (depth == 1 && ApplicationLoader.RNG_NAMESPACE.equals(nsURI)) {
                if ("define".equals(name)) {
                  attributeHandler = new NanoXmlUtil.IXMLBuilderAdapter() {
                    @Override
                    public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
                      if ("name".equals(key) && (nsURI == null || nsURI.length() == 0) && value != null) {
                        map.put(value, null);
                      }
                    }
                  };
                }
              }
              depth++;
            }

            @Override
            public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
              attributeHandler = null;
              depth--;
            }
          });
        } else if (inputData.getFileType() == RncFileType.getInstance()) {
          final PsiFile file = inputData.getPsiFile();
          if (file instanceof XmlFile) {
            final Grammar grammar = GrammarFactory.getGrammar((XmlFile)file);
            if (grammar != null) {
              grammar.acceptChildren(new CommonElement.Visitor() {
                @Override
                public void visitDefine(Define define) {
                  final String name = define.getName();
                  if (name != null) {
                    map.put(name, null);
                  }
                }
              });
            }
          }
        }
        return map;
      }
    };
  }

  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return new EnumeratorStringDescriptor();
  }

  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new FileBasedIndex.InputFilter() {
      @Override
      public boolean acceptInput(VirtualFile file) {
        if (file.getFileSystem() instanceof JarFileSystem) {
          return false; // there is lots and lots of custom XML inside zip files
        }
        return file.getFileType() == StdFileTypes.XML || file.getFileType() == RncFileType.getInstance();
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 0;
  }

  private static class SymbolCollector implements FileBasedIndex.ValueProcessor<Void> {
    private final GlobalSearchScope myScope;
    private final PsiManager myMgr;
    private final String myName;

    private final Collection<NavigationItem> myResult = new ArrayList<NavigationItem>();

    public SymbolCollector(String name, Project project, GlobalSearchScope scope) {
      myMgr = PsiManager.getInstance(project);
      myScope = scope;
      myName = name;
    }

    @Override
    public boolean process(VirtualFile file, Void kind) {
      if (myScope.contains(file)) {
        final PsiFile psiFile = myMgr.findFile(file);
        if (psiFile instanceof XmlFile) {
          final Grammar grammar = GrammarFactory.getGrammar((XmlFile)psiFile);
          if (grammar != null) {
            grammar.acceptChildren(new CommonElement.Visitor() {
              @Override
              public void visitDefine(Define define) {
                if (myName.equals(define.getName())) {
                  final PsiElement psi = define.getPsiElement();
                  if (psi != null) {
                    MyNavigationItem.add((NavigationItem)define.getPsiElement(), myResult);
                  }
                }
              }
            });
          }
        }
      }
      return true;
    }

    public NavigationItem[] getResult() {
      return myResult.toArray(new NavigationItem[myResult.size()]);
    }
  }

  private static class MyNavigationItem implements PsiElementNavigationItem, ItemPresentation {
    private final NavigationItem myItem;
    private final ItemPresentation myPresentation;

    private MyNavigationItem(NavigationItem item, @NotNull final ItemPresentation presentation) {
      myItem = item;
      myPresentation = presentation;
    }

    @Override
    public String getPresentableText() {
      return myPresentation.getPresentableText();
    }

    @Override
    @Nullable
    public String getLocationString() {
      return getLocationString((PsiElement)myItem);
    }

    private static String getLocationString(PsiElement element) {
      return "(in " + element.getContainingFile().getName() + ")";
    }

    @Override
    @Nullable
    public Icon getIcon(boolean open) {
      return myPresentation.getIcon(open);
    }

    @Nullable
    public TextAttributesKey getTextAttributesKey() {
      return myPresentation instanceof ColoredItemPresentation ? ((ColoredItemPresentation) myPresentation).getTextAttributesKey() : null;
    }

    @Override
    public String getName() {
      return myItem.getName();
    }

    @Override
    public ItemPresentation getPresentation() {
      return myPresentation != null ? this : null;
    }

    @Override
    public PsiElement getTargetElement() {
      return (PsiElement) myItem;
    }

    @Override
    public void navigate(boolean requestFocus) {
      myItem.navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
      return myItem.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
      return myItem.canNavigateToSource();
    }

    public static void add(final NavigationItem item, Collection<NavigationItem> symbolNavItems) {
      final ItemPresentation presentation;
      if (item instanceof PsiMetaOwner) {
        final PsiMetaData data = ((PsiMetaOwner)item).getMetaData();
        if (data instanceof PsiPresentableMetaData) {
          final PsiPresentableMetaData metaData = (PsiPresentableMetaData)data;
          presentation = new ColoredItemPresentation() {
            @Override
            public String getPresentableText() {
              return metaData.getName();
            }

            @Override
            @Nullable
            public String getLocationString() {
              return MyNavigationItem.getLocationString((PsiElement)item);
            }

            @Override
            @Nullable
            public Icon getIcon(boolean open) {
              return metaData.getIcon();
            }

            @Nullable
            @Override
            public TextAttributesKey getTextAttributesKey() {
              final ItemPresentation p = item.getPresentation();
              return p instanceof ColoredItemPresentation ? ((ColoredItemPresentation) p).getTextAttributesKey() : null;
            }
          };
        } else {
          presentation = item.getPresentation();
        }
      } else {
        presentation = item.getPresentation();
      }

      if (presentation != null) {
        symbolNavItems.add(new MyNavigationItem(item, presentation));
      }
    }
  }
}

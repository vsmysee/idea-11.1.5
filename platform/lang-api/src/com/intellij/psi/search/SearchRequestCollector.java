package com.intellij.psi.search;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.util.Processor;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
* @author peter
*/
public class SearchRequestCollector {
  private final Object lock = new Object();
  private final List<PsiSearchRequest> myWordRequests = CollectionFactory.arrayList();
  private final List<QuerySearchRequest> myQueryRequests = CollectionFactory.arrayList();
  private final List<Processor<Processor<PsiReference>>> myCustomSearchActions = CollectionFactory.arrayList();
  private final SearchSession mySession;

  public SearchRequestCollector(SearchSession session) {
    mySession = session;
  }

  public SearchSession getSearchSession() {
    return mySession;
  }

  public void searchWord(@NotNull String word, @NotNull SearchScope searchScope, boolean caseSensitive, @NotNull PsiElement searchTarget) {
    final short searchContext = (short)(UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES | UsageSearchContext.IN_COMMENTS
                                | (searchTarget instanceof PsiFileSystemItem ? UsageSearchContext.IN_STRINGS : 0));
    searchWord(word, searchScope, searchContext, caseSensitive, searchTarget);
  }

  public void searchWord(@NotNull String word, @NotNull SearchScope searchScope, short searchContext, boolean caseSensitive, @NotNull PsiElement searchTarget) {
    searchWord(word, searchScope, searchContext, caseSensitive, new SingleTargetRequestResultProcessor(searchTarget));
  }

  public void searchWord(@NotNull String word, @NotNull SearchScope searchScope, short searchContext, boolean caseSensitive, @NotNull RequestResultProcessor processor) {
    if (searchScope instanceof LocalSearchScope && ((LocalSearchScope)searchScope).getScope().length == 0) {
      return;
    }

    synchronized (lock) {
      myWordRequests.add(new PsiSearchRequest(searchScope, word, searchContext, caseSensitive, processor));
    }
  }

  public void searchQuery(QuerySearchRequest request) {
    assert request.collector != this;
    assert request.collector.getSearchSession() == mySession;
    synchronized (lock) {
      myQueryRequests.add(request);
    }
  }

  public void searchCustom(Processor<Processor<PsiReference>> searchAction) {
    synchronized (lock) {
      myCustomSearchActions.add(searchAction);
    }
  }

  public List<QuerySearchRequest> takeQueryRequests() {
    return takeRequests(myQueryRequests);
  }

  private <T> List<T> takeRequests(List<T> list) {
    synchronized (lock) {
      final List<T> requests = new ArrayList<T>(list);
      requests.addAll(list);
      list.clear();
      return requests;
    }
  }

  public List<PsiSearchRequest> takeSearchRequests() {
    return takeRequests(myWordRequests);
  }

  public List<Processor<Processor<PsiReference>>> takeCustomSearchActions() {
    return takeRequests(myCustomSearchActions);
  }

  @Override
  public String toString() {
    return myWordRequests.toString().replace(',', '\n');
  }
}

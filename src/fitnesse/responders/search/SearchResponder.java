// Copyright (C) 2003-2009 by Object Mentor, Inc. All rights reserved.
// Released under the terms of the CPL Common Public License version 1.0.
package fitnesse.responders.search;

import java.util.regex.Pattern;

import fitnesse.components.TraversalListener;
import fitnesse.wiki.search.RegularExpressionWikiPageFinder;
import fitnesse.wiki.search.TitleWikiPageFinder;

import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.LITERAL;

public class SearchResponder extends ResultResponder {

  private String getSearchString() {
    String searchString = request.getInput("searchString");
    return searchString == null ? "" : searchString;
  }

  private String getSearchType() {
    String searchType = request.getInput("searchType");

    if (searchType == null || searchType.toLowerCase().contains("title"))
      return "Title";
    else
      return "Content";
  }

 
  protected String getPageFooterInfo(int hits) {
    return "Found " + hits + " results for your search.";
  }

  @Override
  protected String getTitle() {
    return (request.getInput("searchType") == null) ? "Search Form" : getSearchType() + " Search Results for '" + getSearchString() + "'";
  }

  @Override
  protected String getTemplate() {
    return "searchForm";
  }
  @Override
  public void traverse(TraversalListener<Object> observer) {
    String searchString = getSearchString();
    if (!"".equals(searchString)) {
      String searchType = getSearchType();
      if ("Title".equals(searchType))
        new TitleWikiPageFinder(searchString, observer).search(getSearchScope());
      else {
        Pattern regularExpression = Pattern.compile(searchString, CASE_INSENSITIVE + LITERAL);
        new RegularExpressionWikiPageFinder(regularExpression, observer).search(getSearchScope());
      }
    }
  }

  @Override
  protected boolean shouldRespondWith404() {
    return false;
  }

}

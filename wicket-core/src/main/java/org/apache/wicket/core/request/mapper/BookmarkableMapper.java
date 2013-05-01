/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wicket.core.request.mapper;

import org.apache.wicket.Application;
import org.apache.wicket.core.request.handler.PageProvider;
import org.apache.wicket.core.request.handler.RenderPageRequestHandler;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.component.IRequestablePage;
import org.apache.wicket.request.mapper.info.PageComponentInfo;
import org.apache.wicket.request.mapper.parameter.IPageParameters;
import org.apache.wicket.request.mapper.parameter.IPageParametersEncoder;
import org.apache.wicket.request.mapper.parameter.PageParametersEncoder;
import org.apache.wicket.util.lang.Args;

/**
 * Decodes and encodes the following URLs:
 * 
 * <pre>
 *  Page Class - Render (BookmarkablePageRequestHandler)
 *  /wicket/bookmarkable/org.apache.wicket.MyPage
 *  (will redirect to hybrid alternative if page is not stateless)
 * 
 *  Page Instance - Render Hybrid (RenderPageRequestHandler for pages that were created using bookmarkable URLs)
 *  /wicket/bookmarkable/org.apache.wicket.MyPage?2
 * 
 *  Page Instance - Bookmarkable Listener (BookmarkableListenerInterfaceRequestHandler)
 *  /wicket/bookmarkable/org.apache.wicket.MyPage?2-click-foo-bar-baz
 *  /wicket/bookmarkable/org.apache.wicket.MyPage?2-click.1-foo-bar-baz (1 is behavior index)
 *  (these will redirect to hybrid if page is not stateless)
 * </pre>
 * 
 * @author Matej Knopp
 */
public class BookmarkableMapper extends AbstractBookmarkableMapper
{
	private final IPageParametersEncoder pageParametersEncoder;

	/**
	 * Construct.
	 * 
	 * @param pageParametersEncoder
	 */
	public BookmarkableMapper(IPageParametersEncoder pageParametersEncoder)
	{
		Args.notNull(pageParametersEncoder, "pageParametersEncoder");

		this.pageParametersEncoder = pageParametersEncoder;
	}

	/**
	 * Construct.
	 */
	public BookmarkableMapper()
	{
		this(new PageParametersEncoder());
	}

	/**
	 * @see AbstractBookmarkableMapper#buildUrl(AbstractBookmarkableMapper.UrlInfo)
	 */
	@Override
	protected Url buildUrl(UrlInfo info)
	{
		Url url = new Url();
		url.getSegments().add(getContext().getNamespace());
		url.getSegments().add(getContext().getBookmarkableIdentifier());
		url.getSegments().add(info.getPageClass().getName());

		encodePageComponentInfo(url, info.getPageComponentInfo());

		return encodePageParameters(url, info.getPageParameters(), pageParametersEncoder);
	}

	/**
	 * @see AbstractBookmarkableMapper#parseRequest(org.apache.wicket.request.Request)
	 */
	@Override
	protected UrlInfo parseRequest(Request request)
	{
		Url url = request.getUrl();
		if (matches(url))
		{
			// try to extract page and component information from URL
			PageComponentInfo info = getPageComponentInfo(url);

			// load the page class
			String className = url.getSegments().get(2);
			Class<? extends IRequestablePage> pageClass = getPageClass(className);

			if (pageClass != null && IRequestablePage.class.isAssignableFrom(pageClass))
			{
				if (Application.exists())
				{
					Application application = Application.get();

					if (application.getSecuritySettings().getEnforceMounts())
					{
						// we make an excepion if the homepage itself was mounted, see WICKET-1898
						if (!pageClass.equals(application.getHomePage()))
						{
							// WICKET-5094 only enforce mount if page is mounted
							Url reverseUrl = application.getRootRequestMapper().mapHandler(
								new RenderPageRequestHandler(new PageProvider(pageClass)));
							if (!matches(reverseUrl))
							{
								return null;
							}
						}
					}
				}

				// extract the PageParameters from URL if there are any
				IPageParameters pageParameters = extractPageParameters(request, 3,
					pageParametersEncoder);

				return new UrlInfo(info, pageClass, pageParameters);
			}
		}
		return null;
	}

	/**
	 * @see AbstractBookmarkableMapper#pageMustHaveBeenCreatedBookmarkable()
	 */
	@Override
	protected boolean pageMustHaveBeenCreatedBookmarkable()
	{
		return true;
	}

	/**
	 * @see AbstractBookmarkableMapper#getCompatibilityScore(org.apache.wicket.request.Request)
	 */
	@Override
	public int getCompatibilityScore(Request request)
	{
		int score = 0;
		Url url = request.getUrl();
		if (matches(url))
		{
			score = Integer.MAX_VALUE;
		}
		return score;
	}

	private boolean matches(final Url url)
	{
		return (url.getSegments().size() >= 3 && urlStartsWith(url, getContext().getNamespace(),
			getContext().getBookmarkableIdentifier()));
	}
}

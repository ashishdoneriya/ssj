package com.csetutorials.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.csetutorials.beans.CatTag;
import com.csetutorials.beans.Image;
import com.csetutorials.beans.Page;
import com.csetutorials.beans.Paginator;
import com.csetutorials.beans.SiteConfig;
import com.csetutorials.contants.Layouts;
import com.csetutorials.contants.Paths;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

public class SiteUtils {

	public static SiteConfig getSiteConfig() throws JsonSyntaxException, IOException {
		String json = FileUtils.getString(Paths.getSiteConfigDir());
		Type type = new TypeToken<Map<String, Object>>() {
		}.getType();
		Map<String, Object> rawConfig = Constants.gson.fromJson(json, type);
		SiteConfig config = Constants.gson.fromJson(json, SiteConfig.class);
		config.setRawConfig(rawConfig);
		config.setActiveThemeDir(getActiveThemeDir(config));
		return config;
	}

	private static String getActiveThemeDir(SiteConfig config) {
		String activeTheme = config.getTheme();
		if (activeTheme != null) {

			if (activeTheme.startsWith("https://github.com")) {
				String url = activeTheme;
				if (url.endsWith("/")) {
					url = url.substring(0, url.length() - 1);
				}
				String tree = "master";
				String repo = url;
				if (url.contains("tree")) {
					repo = url.substring(0, url.indexOf("/tree"));
					tree = url.substring(url.lastIndexOf("/") + 1);
				}
				String themeName = repo.substring(repo.lastIndexOf("/") + 1);
				repo = repo + ".git";
				File dir = new File(StringUtils.removeExtraSlash(Paths.getThemesDir() + File.separator + themeName));
				if (dir.exists() && dir.isDirectory() && dir.list().length != 0) {
					return dir.getAbsolutePath();
				} else {
					Collection<Ref> remoteRefs = null;
					try {
						remoteRefs = Git.lsRemoteRepository().setHeads(true).setTags(true).setRemote(repo).call();
					} catch (GitAPIException e) {

						System.out.println("Problem while fetching theme from [" + repo + "]");
						e.printStackTrace();
						System.exit(1);
					}
					Ref ref = null;
					for (Ref temp : remoteRefs) {
						String tempName = temp.getName();
						tempName = tempName.substring(tempName.lastIndexOf("/") + 1);
						if (tempName.equals(tree)) {
							ref = temp;
							break;
						}
					}
					dir.getParentFile().mkdirs();

					try (Git result = Git.cloneRepository().setURI(repo).setDirectory(dir).setBranch(ref.getName())
							.call()) {

					} catch (Exception e) {
						System.out.println("Problem while cloning theme from [" + repo + "]");
					}
					return dir.getAbsolutePath();
				}
			} else {
				File dir = new File(StringUtils.removeExtraSlash(Paths.getThemesDir() + File.separator + activeTheme));
				if (dir.exists() && dir.isDirectory()) {
					return dir.getAbsolutePath();
				} else {
					System.out.println("Invalid theme -" + activeTheme);
					System.exit(1);
				}
			}

		}
		File dir = new File(Paths.getThemesDir());
		if (!dir.exists() || !dir.isDirectory() || dir.list().length == 0) {
			System.out.println("No theme found");
			System.exit(1);
		}
		File[] themes = dir.listFiles();
		if (themes.length > 1) {
			System.out.println("Kindly set atleast one theme using field 'theme'");
			System.exit(1);
		}
		return themes[0].getAbsolutePath();
	}

	private static Map<String, Object> createMap(Page page) throws IOException {
		Map<String, Object> map = new HashMap<>();
		for (Entry<String, Object> e : page.getRawParams().entrySet()) {
			map.put(e.getKey(), e.getValue());
		}
		map.put("title", page.getTitle());
		map.put("layout", page.getLayout());
		map.put("slug", page.getSlug());
		map.put("permalink", page.getPermalink());
		map.put("url", page.getUrl());
		map.put("created", page.getCreated());
		map.put("updated", page.getUpdated());
		map.put("author", page.getAuthor());
		map.put("categories", page.getCategories());
		map.put("tags", page.getTags());
		map.put("isDraft", page.getIsDraft());
		map.put("summary", page.getSummary());
		map.put("absoluteUrl", page.getAbsoluteUrl());
		return map;
	}

	public static void generateLatestPostsPages(SiteConfig siteConfig) throws FileNotFoundException {
		if (!TemplateUtils.isTemplateAvailable(siteConfig, Layouts.latestPosts)) {
			return;
		}

		VelocityEngine engine = siteConfig.getEngine();
		VelocityContext context = new VelocityContext();

		context.put("site", siteConfig.getRawConfig());
		context.put("contentType", "latestPosts");
		context.put("data", siteConfig.getData());
		context.put("dateUtils", new DateUtils());

		int maxPosts = siteConfig.getMaxPosts();
		List<Page> posts = siteConfig.getPosts();
		if (!siteConfig.isPaginationEnabled()) {
			maxPosts = posts.size();
		}
		int totalPages = (int) Math.ceil(posts.size() / (maxPosts * 1.0));
		for (int i = 1; i <= totalPages; i++) {
			List<Page> list = getSublist(posts, i, maxPosts);
			Paginator paginator = new Paginator();
			paginator.setCurrentPage(i);
			paginator.setHasNextPage(i != totalPages);
			paginator.setHasPreviousPage(i != 1);
			paginator.setPosts(list);
			paginator.setPostsPerPage(maxPosts);
			paginator.setTotalPages(totalPages);
			paginator.setTotalPosts(posts.size());

			if (i != totalPages) {
				String nextPageUrl = siteConfig.getBaseUrl() + "/" + siteConfig.getIndexPostsBase() + "/page/" + i;
				nextPageUrl = StringUtils.removeExtraSlash(nextPageUrl);
				paginator.setNextPageUrl(nextPageUrl);
			} else if (i != 1) {
				String previousPageUrl = siteConfig.getBaseUrl() + "/" + siteConfig.getIndexPostsBase()
						+ (i == 2 ? "" : "/page/" + i);
				previousPageUrl = StringUtils.removeExtraSlash(previousPageUrl);
				paginator.setPreviousPageUrl(previousPageUrl);
			}
			String currentPageFilePath = Paths.getGeneratedHtmlDir() + File.separator + siteConfig.getBaseUrl()
					+ File.separator + siteConfig.getIndexPostsBase() + File.separator
					+ (i == 1 ? "index.html" : "/page" + File.separator + i + File.separator + "index.html");
			context.put("paginator", paginator);
			String pageLayoutContent = TemplateUtils.formatContent(engine, context, siteConfig.getLatestPostsLayout());
			pageLayoutContent = Jsoup.parse(pageLayoutContent).toString();
			FileUtils.write(currentPageFilePath, pageLayoutContent);
		}
	}

	public static void generateCategoriesPages(SiteConfig siteConfig, Map<CatTag, List<Page>> catsWithRelatedPosts)
			throws FileNotFoundException {
		if (!TemplateUtils.isTemplateAvailable(siteConfig, Layouts.category)) {
			return;
		}

		VelocityEngine engine = siteConfig.getEngine();
		VelocityContext context = new VelocityContext();
		context.put("site", siteConfig.getRawConfig());
		context.put("contentType", "category");
		context.put("data", siteConfig.getData());
		context.put("dateUtils", new DateUtils());

		for (Entry<CatTag, List<Page>> entry : catsWithRelatedPosts.entrySet()) {
			CatTag cat = entry.getKey();
			List<Page> catPosts = entry.getValue();
			int maxPosts = siteConfig.getMaxPosts();
			if (!siteConfig.isPaginationEnabled()) {
				maxPosts = catPosts.size();
			}
			int totalPages = (int) Math.ceil(catPosts.size() / (maxPosts * 1.0));
			for (int i = 1; i <= totalPages; i++) {
				List<Page> list = getSublist(catPosts, i, maxPosts);

				Paginator paginator = new Paginator();
				paginator.setCurrentPage(i);
				paginator.setHasNextPage(i != totalPages);
				paginator.setHasPreviousPage(i != 1);
				paginator.setPosts(list);
				paginator.setPostsPerPage(maxPosts);
				paginator.setTotalPages(totalPages);
				paginator.setTotalPosts(catPosts.size());

				if (i != totalPages) {
					String nextPageUrl = siteConfig.getBaseUrl() + "/" + siteConfig.getCategoryBase() + "/"
							+ cat.getShortcode() + "/page/" + i;
					nextPageUrl = StringUtils.removeExtraSlash(nextPageUrl);
					paginator.setNextPageUrl(nextPageUrl);
				} else if (i != 1) {
					String previousPageUrl = siteConfig.getBaseUrl() + "/" + siteConfig.getCategoryBase() + "/"
							+ cat.getShortcode() + (i == 2 ? "" : "/page/" + i);
					previousPageUrl = StringUtils.removeExtraSlash(previousPageUrl);
					paginator.setPreviousPageUrl(previousPageUrl);
				}
				String currentPageFilePath = Paths.getGeneratedHtmlDir() + File.separator + siteConfig.getBaseUrl()
						+ File.separator + siteConfig.getCategoryBase() + File.separator + cat.getShortcode()
						+ File.separator
						+ (i == 1 ? "index.html" : "/page" + File.separator + i + File.separator + "index.html");
				context.put("paginator", paginator);
				context.put("category", cat);
				String pageLayoutContent = TemplateUtils.formatContent(engine, context,
						siteConfig.getCategoriesLayout());
				pageLayoutContent = Jsoup.parse(pageLayoutContent).toString();
				FileUtils.write(currentPageFilePath, pageLayoutContent);
			}
		}
	}

	public static void generateTagsPages(SiteConfig siteConfig, Map<CatTag, List<Page>> tagsWithRelatedPosts)
			throws FileNotFoundException {
		if (!TemplateUtils.isTemplateAvailable(siteConfig, Layouts.tag)) {
			return;
		}

		VelocityEngine engine = siteConfig.getEngine();
		VelocityContext context = new VelocityContext();
		context.put("site", siteConfig.getRawConfig());
		context.put("contentType", "tag");
		context.put("data", siteConfig.getData());
		context.put("dateUtils", new DateUtils());

		for (Entry<CatTag, List<Page>> entry : tagsWithRelatedPosts.entrySet()) {
			CatTag tag = entry.getKey();
			List<Page> tagPosts = entry.getValue();
			int maxPosts = siteConfig.getMaxPosts();
			if (!siteConfig.isPaginationEnabled()) {
				maxPosts = tagPosts.size();
			}
			int totalPages = (int) Math.ceil(tagPosts.size() / (maxPosts * 1.0));
			for (int i = 1; i <= totalPages; i++) {
				List<Page> list = getSublist(tagPosts, i, maxPosts);
				Paginator paginator = new Paginator();
				paginator.setCurrentPage(i);
				paginator.setHasNextPage(i != totalPages);
				paginator.setHasPreviousPage(i != 1);
				paginator.setPosts(list);
				paginator.setPostsPerPage(maxPosts);
				paginator.setTotalPages(totalPages);
				paginator.setTotalPosts(tagPosts.size());

				if (i != totalPages) {
					String nextPageUrl = siteConfig.getBaseUrl() + "/" + siteConfig.getTagBase() + "/" + tag.getName()
							+ "/page/" + i;
					nextPageUrl = StringUtils.removeExtraSlash(nextPageUrl);
					paginator.setNextPageUrl(nextPageUrl);
				} else if (i != 1) {
					String previousPageUrl = siteConfig.getBaseUrl() + "/" + siteConfig.getTagBase() + "/"
							+ tag.getName() + (i == 2 ? "" : "/page/" + i);
					previousPageUrl = StringUtils.removeExtraSlash(previousPageUrl);
					paginator.setPreviousPageUrl(previousPageUrl);
				}
				String currentPageFilePath = Paths.getGeneratedHtmlDir() + File.separator + siteConfig.getBaseUrl()
						+ File.separator + siteConfig.getTagBase() + File.separator + tag.getName() + File.separator
						+ (i == 1 ? "index.html" : "/page" + File.separator + i + File.separator + "index.html");
				context.put("paginator", paginator);
				context.put("tag", tag);
				String pageLayoutContent = TemplateUtils.formatContent(engine, context, siteConfig.getTagsLayout());
				pageLayoutContent = Jsoup.parse(pageLayoutContent).toString();
				FileUtils.write(currentPageFilePath, pageLayoutContent);
			}
		}
	}

	private static List<Page> getSublist(List<Page> list, int pageNumber, int maxPosts) {
		List<Page> sub = new ArrayList<>();
		int i = (pageNumber - 1) * maxPosts;
		while (i < list.size() && sub.size() < maxPosts) {
			sub.add(list.get(i++));
		}
		return sub;
	}

	public static void generatePosts(List<Page> pages, SiteConfig siteConfig, boolean isPost,
			JsonSchemaUtils jsonSchemaUtils) throws IOException {
		VelocityContext context = new VelocityContext();
		VelocityEngine engine = siteConfig.getEngine();
		context.put("site", siteConfig.getRawConfig());
		context.put("contentType", isPost ? "post" : "page");
		context.put("data", siteConfig.getData());
		context.put("dateUtils", new DateUtils());
		context.put("StringUtils", StringUtils.class);
		final String metaTagsFormat = FileUtils.getResourceContent("post-meta-tags.html");
		TemplateUtils.addTemplate(siteConfig, "ssj-meta-tags", metaTagsFormat);
		for (Page page : pages) {
			if (page.getIsDraft()) {
				continue;
			}
			Map<String, Object> map = createMap(page);
			context.put("page", map);
			String content = StringUtils.getContentBody(FileUtils.getString(page.getFile()));
			if (page.getFile().getName().endsWith(".md") || page.getFile().getName().endsWith(".markdown")) {
				content = TemplateUtils.parseMarkdown(content);
			}
			TemplateUtils.addTemplate(siteConfig, "test-template-ssr", content);
			content = TemplateUtils.formatContent(engine, context, "test-template-ssr");
			List<Image> images = extractImages(siteConfig, content);
			content = formatAnchorTags(siteConfig, content);
			page.setImages(images);

			map.put("content", content);
			context.put("page", map);
			context.put("jsonSchema", jsonSchemaUtils.generatePageSchema(page));
			final String metaTags = TemplateUtils.formatContent(engine, context, "ssj-meta-tags");
			context.put("seoSettings", metaTags);
			String postLayoutContent = TemplateUtils.formatContent(engine, context, page.getLayout());
			postLayoutContent = Jsoup.parse(postLayoutContent).toString();
			write(page.getPermalink(), postLayoutContent, siteConfig,
					isPost ? siteConfig.isPostUglyUrlEnabled() : siteConfig.isPageUglyUrlEnabled());
		}
	}

	private static List<Image> extractImages(SiteConfig siteConfig, String content) {
		Document doc = Jsoup.parse(content);
		Elements imageElements = doc.select("img");
		List<Image> images = new ArrayList<>(1);
		for (Element imageEle : imageElements) {
			String src = imageEle.attr("src");

			if (!src.startsWith("http")) {
				if (!src.startsWith("/") && !siteConfig.getBaseUrl().equals("/")) {
					src = siteConfig.getBaseUrl() + src;
				}
				src = siteConfig.getUrl() + "/" + src;
				src = StringUtils.removeExtraSlash(src);
			}

			String alt = imageEle.attr("alt");
			if (StringUtils.isBlank(alt)) {
				int slashLastIndex = src.lastIndexOf('/');
				if (slashLastIndex != -1) {
					alt = src.substring(slashLastIndex + 1);
				}
				alt = alt.split("\\.")[0].replaceAll("-", " ");
			}
			Image image = new Image();
			image.setAlt(alt);
			image.setSrc(src);
			images.add(image);
		}
		return images;
	}
	
	private static String formatAnchorTags(SiteConfig siteConfig, String content) {
		Document doc = Jsoup.parse(content);
		Elements anchorTags = doc.select("a");
		for (Element imageEle : anchorTags) {
			String href = imageEle.attr("href");
			if (!href.startsWith("http")) {
				continue;
			}
			imageEle.attr("target", "_blank");
			imageEle.attr("rel", "noopener nofollow");
		}
		return doc.toString();
	}

	public static void write(String permalink, String content, SiteConfig siteConfig, boolean uglyUrl)
			throws FileNotFoundException {
		permalink = "/" + permalink + (uglyUrl ? "" : "/index.html");
		String path = Paths.getGeneratedHtmlDir() + permalink;
		path = path.replaceAll("/+", "/").replaceAll("/", File.separator);
		File file = new File(path);
		file.getParentFile().mkdirs();
		FileUtils.write(file.getAbsolutePath(), content);
	}

	public static void writePost(String postPermalink, String postContent, SiteConfig siteConfig)
			throws FileNotFoundException {
		postPermalink = "/" + postPermalink + (siteConfig.isPostUglyUrlEnabled() ? "" : "/index.html");
		String path = Paths.getGeneratedHtmlDir() + postPermalink;
		path = path.replaceAll("/+", "/").replaceAll("/", File.separator);
		File file = new File(path);
		file.getParentFile().mkdirs();
		FileUtils.write(file.getAbsolutePath(), postContent);
	}

	public static String getCotentFormatted(VelocityContext context, String content) {
		// Initialize the engine.
		VelocityEngine engine = new VelocityEngine();
		engine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
				"org.apache.velocity.runtime.log.Log4JLogChute");
		engine.setProperty(Velocity.RESOURCE_LOADER, "string");
		engine.addProperty("string.resource.loader.class", StringResourceLoader.class.getName());
		engine.addProperty("string.resource.loader.repository.static", "false");
		// engine.addProperty("string.resource.loader.modificationCheckInterval", "1");
		engine.init();

		// Initialize my template repository. You can replace the "Hello $w" with your
		// String.
		StringResourceRepository repo = (StringResourceRepository) engine
				.getApplicationAttribute(StringResourceLoader.REPOSITORY_NAME_DEFAULT);
		repo.putStringResource("temp", content);

		// Get and merge the template with my parameters.
		Template template = engine.getTemplate("temp");
		StringWriter writer = new StringWriter();
		template.merge(context, writer);

		return writer.toString();
	}

	public static void generateAuthorsPages(SiteConfig siteConfig, Map<String, List<Page>> authorsPostsMap)
			throws FileNotFoundException {
		if (!TemplateUtils.isTemplateAvailable(siteConfig, Layouts.author)) {
			return;
		}

		VelocityEngine engine = siteConfig.getEngine();
		VelocityContext context = new VelocityContext();
		context.put("site", siteConfig.getRawConfig());
		context.put("contentType", "author");
		context.put("data", siteConfig.getData());
		context.put("dateUtils", new DateUtils());
		for (Entry<String, List<Page>> entry : authorsPostsMap.entrySet()) {
			String authorName = entry.getKey();
			List<Page> authorPosts = entry.getValue();
			int maxPosts = siteConfig.getMaxPosts();
			if (!siteConfig.isPaginationEnabled()) {
				maxPosts = authorPosts.size();
			}
			int totalPages = (int) Math.ceil(authorPosts.size() / (maxPosts * 1.0));
			for (int i = 1; i <= totalPages; i++) {
				List<Page> list = getSublist(authorPosts, i, maxPosts);
				Paginator paginator = new Paginator();
				paginator.setCurrentPage(i);
				paginator.setHasNextPage(i != totalPages);
				paginator.setHasPreviousPage(i != 1);
				paginator.setPosts(list);
				paginator.setPostsPerPage(maxPosts);
				paginator.setTotalPages(totalPages);
				paginator.setTotalPosts(authorPosts.size());

				if (i != totalPages) {
					String nextPageUrl = siteConfig.getBaseUrl() + "/" + siteConfig.getAuthorBase() + "/" + authorName
							+ "/page/" + i;
					nextPageUrl = StringUtils.removeExtraSlash(nextPageUrl);
					paginator.setNextPageUrl(nextPageUrl);
				} else if (i != 1) {
					String previousPageUrl = siteConfig.getBaseUrl() + "/" + siteConfig.getAuthorBase() + "/"
							+ authorName + (i == 2 ? "" : "/page/" + i);
					previousPageUrl = StringUtils.removeExtraSlash(previousPageUrl);
					paginator.setPreviousPageUrl(previousPageUrl);
				}
				String currentPageFilePath = Paths.getGeneratedHtmlDir() + File.separator + siteConfig.getBaseUrl()
						+ File.separator + siteConfig.getAuthorBase() + File.separator + authorName + File.separator
						+ (i == 1 ? "index.html" : "/page" + File.separator + i + File.separator + "index.html");
				context.put("paginator", paginator);
				context.put("author", list.get(0).getAuthor());
				String pageLayoutContent = TemplateUtils.formatContent(engine, context, siteConfig.getAuthorLayout());
				pageLayoutContent = Jsoup.parse(pageLayoutContent).toString();
				FileUtils.write(currentPageFilePath, pageLayoutContent);
			}
		}
	}

}

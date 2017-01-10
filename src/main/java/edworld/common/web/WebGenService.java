package edworld.common.web;

import static edworld.common.infra.ResourceXML.xmlElement;
import static edworld.common.infra.util.HTMLUtil.escapeHTML;
import static edworld.common.infra.util.RegexUtil.listarOcorrencias;
import static edworld.common.infra.util.RegexUtil.regexHTML;
import static edworld.common.infra.util.TextUtil.LINE_BREAK;
import static edworld.common.infra.util.TextUtil.format;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.beanutils.PropertyUtilsBean;
import org.apache.commons.io.IOUtils;

import edworld.common.core.Link;
import edworld.common.core.entity.VersionedEntity;
import edworld.common.infra.Config;
import edworld.common.infra.util.HTMLUtil;
import edworld.webgen.WebArtifact;
import edworld.webgen.WebInterface;
import edworld.webgen.WebTemplateFinder;

public class WebGenService extends Service {
	public static final String CHARSET_CONFIG = "; charset=UTF-8";
	public static final String HTML = MediaType.TEXT_HTML + CHARSET_CONFIG;
	public static final String DISPLAY_HTML = "___HTML";
	public static final String ITEM_SEPARATOR = "──────────";
	public static final String HTML_OPTION_SEPARATOR = "<option disabled>" + ITEM_SEPARATOR + "</option>";
	public static final String LANGUAGE = "pt";
	protected static final Pattern REGEX_SELECTED_OPTION = regexHTML(
			"(?is)<select...(\\s+value=\"___\")...>(.+?)</select>");
	protected static final Pattern REGEX_CHECKED_INPUT = regexHTML(
			"(?is)<input...type=\"checkbox\"...(\\s+value=\"(S___|N___|)\")...>");
	private static Pattern REGEX_CURRENT_URI = regexHTML("\"CURRENT_URI@(___)\"");

	/**
	 * Override this method to define a custom finder for alternative WEB
	 * component templates.
	 */
	protected WebTemplateFinder getTemplateFinder() {
		return new WebTemplateFinder(null) {
			@Override
			protected InputStream streamFromResourceName(String resourceName) {
				try {
					URL libURL = null;
					for (Enumeration<URL> urls = WebGenService.class.getClassLoader().getResources(resourceName); urls
							.hasMoreElements();) {
						URL url = urls.nextElement();
						if (!url.toString().contains("/lib/"))
							return url.openStream();
						if (libURL == null || !url.toString().contains("/lib/webgen"))
							libURL = url;
					}
					return libURL == null ? null : libURL.openStream();
				} catch (IOException e) {
					throw new IllegalArgumentException(e);
				}
			}
		};
	}

	/**
	 * Override this method to translate current URI according to the given
	 * parameter (e.g. language parameter).
	 */
	protected String translateCurrentURI(String currentURI, String parameter) {
		return currentURI;
	}

	/**
	 * Override this method to check or change content of each WebArtifact found
	 * by <code>getPage</code> method.
	 */
	protected void foundWebArtifact(WebArtifact webArtifact, String page) {
	}

	/**
	 * Override this method to define a WebGen data dictionary.
	 */
	protected String getDataDictionary() {
		return null;
	}

	public Response getStaticResource(String resourceName, String resourceType) {
		byte[] result = getWebResource(resourceName);
		if (result == null)
			return Response.status(Response.Status.NOT_FOUND).build();
		return Response.ok(result, resourceType).build();
	}

	protected Response getPage(String page, Object entity, String rootPath, Map<String, String> fields,
			String... xmlData) {
		WebArtifact webArtifact = getWebArtifact(page, entity, xmlData);
		if (webArtifact == null)
			return Response.status(Response.Status.NOT_FOUND)
					.entity(resultPage("Modelo da página \"" + page + "\" não localizado.", rootPath)).type(HTML)
					.build();
		foundWebArtifact(webArtifact, page);
		applyCurrentURITranslation(webArtifact);
		return Response.ok(content(webArtifact, entity, rootPath, request, getUserPrincipal(), fields)).type(HTML)
				.encoding(Config.getEncoding()).build();
	}

	protected void applyCurrentURITranslation(WebArtifact webArtifact) {
		Matcher matcher = REGEX_CURRENT_URI.matcher(webArtifact.getContent());
		while (matcher.find())
			webArtifact.setContent(webArtifact.getContent().replace(matcher.group(),
					"\"" + translateCurrentURI(getCurrentURI(), matcher.group(1)) + "\""));
	}

	protected WebArtifact getWebArtifact(String page, Object entity, String... xmlData) {
		List<String> dataList = new ArrayList<>();
		for (String data : xmlData)
			dataList.add(data);
		return createWebArtifact(page, getData(dataList));
	}

	protected WebArtifact createWebArtifact(String page, String data) {
		String specification = text("/specifications/" + page + ".wiki");
		if (specification == null)
			return null;
		WebInterface dynInterface = new WebInterface(specification, getDataDictionary(), LANGUAGE, getTemplateFinder(),
				data);
		dynInterface.generateArtifacts();
		return dynInterface.getArtifacts().get(0);
	}

	protected String getData(List<String> listaDados) {
		String dados = "<dados><default>";
		for (String item : listaDados)
			dados += item;
		dados += "</default></dados>";
		return dados;
	}

	protected String text(String path) {
		InputStream stream = WebGenService.class.getResourceAsStream(path);
		if (stream == null)
			return null;
		try {
			return IOUtils.toString(stream, Config.getEncoding());
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
	}

	protected String content(WebArtifact webArtifact, Object entity, String rootPath, HttpServletRequest request,
			Principal principal, Map<String, String> fields) {
		Map<String, String> parameters = prepareParameters(request);
		String html = HTMLUtil.fillRootPath(webArtifact.getContent(), rootPath);
		html = html.replace("${login}", principal.getName());
		for (String field : listarOcorrencias(regexHTML("\\[\\[(.*?(\\[\\d+\\])?)\\]\\]"), html)) {
			html = html.replace("\"[[" + field + "]]\"",
					"\"" + escapeHTML(entityAttribute(entity, field.replace("\"", ""), parameters, fields)) + "\"");
			html = html.replace("[[" + field + "]]", entityAttribute(entity, field, parameters, fields));
		}
		html = solveOptionSeparator(html);
		html = solveSelectedOption(html);
		return solveCheckedInput(html);
	}

	protected String addLinesBeforeMarker(String marker, String content, String... lines) {
		String result = content;
		for (String line : lines) {
			int pos = result.indexOf(marker);
			result = result.substring(0, pos) + line + LINE_BREAK + result.substring(pos);
		}
		return result;
	}

	protected Map<String, String> prepareParameters(HttpServletRequest request) {
		Map<String, String> result = new HashMap<>();
		for (Object parameter : request.getParameterMap().keySet()) {
			String field = (String) parameter;
			if (request.getParameter(field) != null && !request.getParameter(field).isEmpty())
				result.put(field, request.getParameter(field));
		}
		return result;
	}

	protected String entityAttribute(Object entity, String field, Map<String, String> parameters,
			Map<String, String> fields) {
		if (fields != null && fields.containsKey(field))
			return fields.get(field);
		String result = "";
		boolean escapeMarkup = (!field.endsWith("_link") && !field.endsWith("_selecionada")
				&& !field.endsWith(DISPLAY_HTML));
		String property = field.replace(DISPLAY_HTML, "");
		if (entity != null && entity instanceof VersionedEntity)
			try {
				Object attribute;
				try {
					attribute = new PropertyUtilsBean().getProperty(entity, solveField(property));
					if (attribute instanceof Link)
						escapeMarkup = false;
				} catch (ArrayIndexOutOfBoundsException e) {
					attribute = null;
				} catch (NullPointerException e) {
					attribute = null;
				}
				result = format(attribute);
			} catch (Exception e) {
				throw new IllegalArgumentException(e);
			}
		else if (parameters.containsKey(property) && !parameters.get(property).isEmpty())
			result = parameters.get(property);
		return escapeMarkup ? escapeHTML(result) : result;
	}

	public String solveField(String field) {
		if (field.equals("n") || field.startsWith("n_"))
			return "id";
		if (field.startsWith("tipo_"))
			return "tipo";
		String solvedField = field.replace("-", "").replaceAll("_d[aeo]_", "_");
		while (solvedField.contains("_")) {
			int pos = solvedField.indexOf('_');
			if (pos == solvedField.length() - 1)
				solvedField = solvedField.substring(0, pos);
			else
				solvedField = solvedField.substring(0, pos) + solvedField.substring(pos + 1, pos + 2).toUpperCase()
						+ solvedField.substring(pos + 2);
		}
		return solvedField;
	}

	public String solveOptionSeparator(String html) {
		return html.replace(xmlElement("option", ITEM_SEPARATOR), HTML_OPTION_SEPARATOR);
	}

	public String solveSelectedOption(String html) {
		Matcher matcher = REGEX_SELECTED_OPTION.matcher(html);
		while (matcher.find())
			if (matcher.group(2).contains(matcher.group(1).trim()))
				return solveSelectedOption(
						html.substring(0, matcher.start(1))
								+ html.substring(matcher.end(1), matcher.start(2)) + matcher.group(2)
										.replace(matcher.group(1).trim(), matcher.group(1).trim() + " selected")
								+ html.substring(matcher.end(2)));
		return html;
	}

	public String solveCheckedInput(String html) {
		Matcher matcher = REGEX_CHECKED_INPUT.matcher(html);
		while (matcher.find()) {
			String checkedMarker = matcher.group(1).contains("Sim") ? " checked" : "";
			return solveCheckedInput(html.substring(0, matcher.start(1)) + " value=\"true\"" + checkedMarker
					+ html.substring(matcher.end(1)));
		}
		return html;
	}
}

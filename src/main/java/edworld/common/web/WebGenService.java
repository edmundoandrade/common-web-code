package edworld.common.web;

import static edworld.common.infra.RecursoXML.elementoXML;
import static edworld.common.infra.util.HTMLUtil.escapeHTML;
import static edworld.common.infra.util.RegexUtil.listarOcorrencias;
import static edworld.common.infra.util.RegexUtil.regexHTML;
import static edworld.common.infra.util.TextUtil.formatar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.beanutils.PropertyUtilsBean;
import org.apache.commons.io.IOUtils;

import edworld.common.core.Link;
import edworld.common.core.entity.EntidadeVersionada;
import edworld.common.infra.Config;
import edworld.common.infra.repo.PersistenceManager;
import edworld.common.infra.util.HTMLUtil;
import edworld.webgen.WebArtifact;
import edworld.webgen.WebInterface;

public class WebGenService extends Service {
	private static String MENU_ROLE = "Menu";
	public static final String CHARSET_CONFIG = "; charset=UTF-8";
	public static final String HTML = MediaType.TEXT_HTML + CHARSET_CONFIG;
	private static final String DISPLAY_HTML = "___HTML";
	private static final String ITEM_SEPARATOR = "──────────";
	private static final String HTML_OPTION_SEPARATOR = "<option disabled>" + ITEM_SEPARATOR + "</option>";
	private static final String LANGUAGE = "pt";
	private static final Pattern REGEX_SELECTED_OPTION = regexHTML(
			"(?is)<select...(\\s+value=\"___\")...>(.+?)</select>");
	private static final Pattern REGEX_CHECKED_INPUT = regexHTML(
			"(?is)<input...type=\"checkbox\"...(\\s+value=\"[SN]___\")...>");

	@GET
	@Path("")
	public Response getHomePage() {
		return Response.temporaryRedirect(getHomePageURI(getCurrentPath())).build();
	}

	/**
	 * Override this method to define a concrete URI to be used as the
	 * application's homepage.
	 */
	protected URI getHomePageURI(String prefix) {
		return getURI(prefix + "index.html");
	}

	@GET
	@Path("/{resource}")
	public Response getResource(@PathParam("resource") String resource) {
		if (resource.endsWith(".html"))
			return getPage(resource.replace(".html", ""), getUserPrincipal(), null, "", getPersistenceManager(),
					getRequest(), getTemplatesDir(), null);
		byte[] result = getWebResource(resource);
		if (result == null)
			return Response.status(Response.Status.NOT_FOUND).build();
		return Response.ok(result, "image/png").build();
	}

	/**
	 * Override this method to define a directory for alternative WEB component
	 * templates.
	 */
	protected File getTemplatesDir() {
		return null;
	}

	@GET
	@Path("/css/{resource}")
	public Response getStyleResource(@PathParam("resource") String resource) {
		byte[] result = getWebResource("css/" + resource);
		if (result == null)
			return Response.status(Response.Status.NOT_FOUND).build();
		return Response.ok(result, "text/css").build();
	}

	@GET
	@Path("/auth/{page}.html")
	public Response getProtectedPage(@PathParam("page") String page) {
		if (!isInRole(MENU_ROLE))
			return unauthorizedAccess();
		return getPage(page, getUserPrincipal(), null, "../", getPersistenceManager(), getRequest(), getTemplatesDir(),
				null);
	}

	public static Response getPage(String page, Principal principal, Object entity, String rootPath,
			PersistenceManager persistenceManager, HttpServletRequest request, File templatesDir,
			Map<String, String> fields, String... xmlData) {
		WebArtifact webArtifact = getWebArtifact(page, principal, entity, persistenceManager, request, templatesDir,
				xmlData);
		if (webArtifact == null)
			return Response.status(Response.Status.NOT_FOUND)
					.entity(resultPage("Modelo da página \"" + page + "\" não localizado.", rootPath)).type(HTML)
					.build();
		return Response.ok(content(webArtifact, entity, rootPath, request, principal, persistenceManager, fields))
				.type(HTML).encoding(Config.getEncoding()).build();
	}

	private static WebArtifact getWebArtifact(String page, Principal principal, Object entity,
			PersistenceManager persistenceManager, HttpServletRequest request, File templatesDir, String... xmlData) {
		List<String> dataList = new ArrayList<>();
		for (String data : xmlData)
			dataList.add(data);
		return createWebArtifact(page, templatesDir, getData(dataList));
	}

	public static WebArtifact createWebArtifact(String page, File templatesDir, String data) {
		String specification = text("/specifications/" + page + ".wiki");
		if (specification == null)
			return null;
		WebInterface dynInterface = new WebInterface(specification, null, LANGUAGE, templatesDir, data);
		dynInterface.generateArtifacts();
		return dynInterface.getArtifacts().get(0);
	}

	private static String getData(List<String> listaDados) {
		String dados = "<dados><default>";
		for (String item : listaDados)
			dados += item;
		dados += "</default></dados>";
		return dados;
	}

	private static String text(String path) {
		InputStream stream = WebGenService.class.getResourceAsStream(path);
		if (stream == null)
			return null;
		try {
			return IOUtils.toString(stream, Config.getEncoding());
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private static String content(WebArtifact webArtifact, Object entity, String rootPath, HttpServletRequest request,
			Principal principal, PersistenceManager persistenceManager, Map<String, String> fields) {
		Map<String, String> parameters = prepareParameters(request, principal, persistenceManager);
		String html = HTMLUtil.fillRootPath(webArtifact.getContent(), rootPath);
		html = html.replace("${login}", principal.getName());
		for (String field : listarOcorrencias(regexHTML("\\[\\[(.*?(\\[\\d+\\])?)\\]\\]"), html))
			html = html.replace("[[" + field + "]]",
					entityAttribute(entity, field, parameters, principal, persistenceManager, fields));
		html = solveOptionSeparator(html);
		html = solveSelectedOption(html);
		return solveCheckedInput(html);
	}

	private static Map<String, String> prepareParameters(HttpServletRequest request, Principal principal,
			PersistenceManager persistenceManager) {
		Map<String, String> result = new HashMap<>();
		for (Object parameter : request.getParameterMap().keySet()) {
			String field = (String) parameter;
			if (request.getParameter(field) != null && !request.getParameter(field).isEmpty())
				result.put(field, request.getParameter(field));
		}
		return result;
	}

	private static String entityAttribute(Object entity, String field, Map<String, String> parameters,
			Principal principal, PersistenceManager persistenceManager, Map<String, String> fields) {
		if (fields != null && fields.containsKey(field))
			return fields.get(field);
		String result = "";
		boolean escapeMarkup = (!field.endsWith("_link") && !field.endsWith("_selecionada")
				&& !field.endsWith(DISPLAY_HTML));
		String property = field.replace(DISPLAY_HTML, "");
		if (entity != null && entity instanceof EntidadeVersionada)
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
				result = formatar(attribute);
			} catch (Exception e) {
				throw new IllegalArgumentException(e);
			}
		else if (parameters.containsKey(property) && !parameters.get(property).isEmpty())
			result = parameters.get(property);
		return escapeMarkup ? escapeHTML(result) : result;
	}

	private static String solveField(String field) {
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

	private static String solveOptionSeparator(String html) {
		return html.replace(elementoXML("option", ITEM_SEPARATOR), HTML_OPTION_SEPARATOR);
	}

	public static String solveSelectedOption(String html) {
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

	public static String solveCheckedInput(String html) {
		Matcher matcher = REGEX_CHECKED_INPUT.matcher(html);
		while (matcher.find()) {
			String checkedMarker = matcher.group(1).contains("Sim") ? " checked" : "";
			return solveCheckedInput(html.substring(0, matcher.start(1)) + " value=\"true\"" + checkedMarker
					+ html.substring(matcher.end(1)));
		}
		return html;
	}
}

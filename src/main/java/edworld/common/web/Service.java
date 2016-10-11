package edworld.common.web;

import static edworld.common.infra.util.DateUtil.parseDate;
import static edworld.common.infra.util.DateUtil.parseTimeStamp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import edworld.common.infra.Config;
import edworld.common.infra.boundary.TimestampCalendar;
import edworld.common.infra.repo.PersistenceManager;
import edworld.common.infra.util.HTMLUtil;
import edworld.common.infra.util.TextUtil;
import edworld.common.web.infra.UserInfo;
import edworld.common.web.infra.WebUtil;

public abstract class Service {
	// @Produces requires static constant values for the format options.
	public static final String CHARSET_CONFIG = "; charset=UTF-8";
	public static final String JSON = MediaType.APPLICATION_JSON + CHARSET_CONFIG;
	public static final String XML = MediaType.APPLICATION_XML + CHARSET_CONFIG;
	public static final String CSV = "text/csv" + CHARSET_CONFIG;
	public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
			+ CHARSET_CONFIG;
	public static final String HTML = MediaType.TEXT_HTML + CHARSET_CONFIG;
	public static final String PLAIN = MediaType.TEXT_PLAIN + CHARSET_CONFIG;
	public static final String CSS = "text/css" + CHARSET_CONFIG;
	public static final String IMAGE = "image/*";
	protected static final String MULTIPART_FORM_DATA = MediaType.MULTIPART_FORM_DATA;
	protected static final String APPLICATION_OCTET_STREAM = MediaType.APPLICATION_OCTET_STREAM;
	protected static final String LINK_HISTORY_BACK = "<p><a href=\"javascript:window.history.back()\">Voltar</a></p>";
	protected static final Pattern WEB_PATH_DELIMITER = Pattern.compile("/");
	@PersistenceContext(unitName = "main")
	protected EntityManager entityManager;
	@Context
	protected HttpServletRequest request;
	@Context
	private ServletContext servletContext;
	private PersistenceManager persistenceManager;
	private Principal userPrincipal;

	public static URI getURI(String uri, String... queryParameters) {
		try {
			String query = "";
			String prefix = "?";
			for (String parameter : queryParameters) {
				String[] parts = parameter.split("=", 2);
				query += prefix + parts[0] + "=" + HTMLUtil.encodeURLParam(parts[1]);
				prefix = "&";
			}
			return new URI(uri + query);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Override this method to change the standard name for view pages.
	 * 
	 * @param prefix
	 */
	protected URI getURIView(String prefix) {
		return getURI(prefix + "view.html");
	}

	/**
	 * Override this method to insert an operation record into the application's
	 * audit trail.
	 * 
	 * @param operation
	 * @param userInfo
	 */
	protected void auditOperation(String operation, UserInfo userInfo) {
	}

	/**
	 * Override this method to define a default value returned by
	 * {@link #getUserPrincipal()} when no user has been authenticated.
	 * 
	 * @return value to be returned by {@link #getUserPrincipal()} when no user
	 *         has been authenticated.
	 */
	protected Principal getDefaultUserPrincipal() {
		return null;
	}

	protected String resultPage(String result, String rootPath) {
		String titulo = "Página de resultado";
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		try {
			String template = IOUtils.toString(Service.class.getResource("/templates/web-page.html"),
					Config.getEncoding());
			PrintStream out = new PrintStream(byteStream, false, Config.getEncoding());
			HTMLUtil.openHTML(template, rootPath, out, titulo);
			out.println("<ol class=\"breadcrumb\">");
			out.println("<li class=\"active\">" + titulo + "</li>");
			out.println("</ol>");
			out.print(result);
			HTMLUtil.closeHTML(template, out);
			out.close();
			return byteStream.toString(Config.getEncoding());
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
	}

	protected HttpServletRequest getRequest() {
		return request;
	}

	protected PersistenceManager getPersistenceManager() {
		if (persistenceManager == null)
			persistenceManager = new PersistenceManager(entityManager);
		return persistenceManager;
	}

	protected UserInfo getUserInfo() {
		return WebUtil.getUserInfo(request);
	}

	protected boolean isInRole(String role) {
		UserInfo pendingLogin = (UserInfo) WebUtil.getSessionAttribute("PendingLogin", request);
		if (pendingLogin != null) {
			auditOperation("Login", pendingLogin);
			WebUtil.setSessionAttribute("PendingLogin", null, request);
		}
		return getUserInfo() != null && getUserInfo().isInRole(role);
	}

	protected String getParameter(String name) {
		return chk(getRequest().getParameter(name));
	}

	protected String chk(String parameter) {
		return TextUtil.chk(parameter);
	}

	protected String[] chk(String[] parameter) {
		return TextUtil.chk(parameter);
	}

	protected Integer chkInt(String parameter) {
		return TextUtil.chkInt(parameter);
	}

	protected BigDecimal chkDec(String parameter) {
		return TextUtil.chkDec(parameter, getThousandSeparator(), getDecimalSeparator());
	}

	protected Double chkDbl(String parameter) {
		return TextUtil.chkDbl(parameter, getThousandSeparator(), getDecimalSeparator());
	}

	protected Boolean chkBool(String parameter, Boolean valorDefault) {
		return TextUtil.chkBool(parameter, valorDefault);
	}

	protected boolean hasContent(String parameter) {
		return TextUtil.hasContent(parameter);
	}

	protected boolean hasContent(String[] parameter) {
		return TextUtil.hasContent(parameter);
	}

	protected String getThousandSeparator() {
		return ".";
	}

	protected String getDecimalSeparator() {
		return ",";
	}

	protected Principal getUserPrincipal() {
		if (userPrincipal == null)
			userPrincipal = getUserInfo() == null ? getDefaultUserPrincipal() : new Principal() {
				public String getName() {
					return getUserInfo().getEmail();
				}
			};
		return userPrincipal;
	}

	protected Response validationErrors(List<String> messages) {
		String html = "<html>";
		html += "<ul>";
		for (String message : messages)
			html += "<li>" + HTMLUtil.escapeHTML(message) + "</li>";
		html += "</ul>";
		html += LINK_HISTORY_BACK;
		html += "</html>";
		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(html).type(HTML).build();
	}

	protected <T> Response response(Object entity, String format, String fileName) {
		ResponseBuilder builder = Response.ok(entity);
		if ("json".equals(format))
			builder.type(JSON);
		else if ("xml".equals(format))
			builder.type(XML);
		else if ("csv".equals(format)) {
			builder.type(CSV);
			builder.header("Content-Disposition", "attachment; filename=" + fileName + "." + format);
		} else if ("xlsx".equals(format)) {
			builder.type(XLSX);
			builder.header("Content-Disposition", "attachment; filename=" + fileName + "." + format);
		}
		return builder.build();
	}

	protected <T> Response responseList(List<T> list, String format, String fileName) {
		return response(new GenericEntity<List<T>>(list) {
		}, format, fileName);
	}

	public Response unauthorizedAccess() {
		return Response.status(Response.Status.UNAUTHORIZED)
				.entity(paginaResultado(
						errorMessage("Acesso não autorizado para '" + getUserPrincipal().getName() + "'.")))
				.type(HTML).build();
	}

	protected Calendar toCalendar(String date) {
		if (!hasContent(date))
			return null;
		return parseDate(date);
	}

	protected TimestampCalendar toTimestamp(String timeStamp) {
		if (!hasContent(timeStamp))
			return null;
		return new TimestampCalendar(parseTimeStamp(timeStamp));
	}

	protected byte[] getWebResource(String resource) {
		return getWebResource(new File(servletContext.getRealPath(resource)));
	}

	protected byte[] getWebResource(File file) {
		if (!file.exists())
			return null;
		try {
			return FileUtils.readFileToByteArray(file);
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
	}

	protected String paginaResultado(String result) {
		return resultPage(result, getRootPath());
	}

	protected String getRootPath() {
		return getRootPath(request.getPathInfo(), request.getServletPath());
	}

	protected String getCurrentPath() {
		return getCurrentPath(request.getRequestURI());
	}

	protected String addLinks(String mensagem, URI uri) {
		return mensagem.replaceAll("consultando\\-([ao]) novamente",
				"<a href=\"" + uri + "\">consultando-$1 novamente</a>");
	}

	protected String successMessage(String mensagem) {
		String html = "<div class=\"alert alert-success\" role=\"alert\">";
		html += "<span class=\"glyphicon glyphicon-exclamation-sign\" aria-hidden=\"true\"></span>";
		html += "<span class=\"sr-only\">Sucesso:</span>";
		html += mensagem;
		html += "</div>";
		return html;
	}

	protected String errorMessage(String mensagem) {
		String html = "<div class=\"alert alert-danger\" role=\"alert\">";
		html += "<span class=\"glyphicon glyphicon-exclamation-sign\" aria-hidden=\"true\"></span>";
		html += "<span class=\"sr-only\">Erro:</span>";
		html += mensagem;
		html += "</div>";
		return html;
	}

	protected String getRootPath(String pathInfo, String servletPath) {
		String absolutePath = servletPath;
		if (pathInfo != null)
			absolutePath += pathInfo;
		Matcher matcher = WEB_PATH_DELIMITER.matcher(absolutePath.substring(1));
		String relativePath = "";
		while (matcher.find())
			relativePath += "../";
		return relativePath;
	}

	protected String getCurrentPath(String requestURI) {
		if (requestURI.endsWith("/"))
			return "";
		String[] parts = requestURI.split("/");
		String result = parts[parts.length - 1];
		return result.isEmpty() ? "" : result + "/";
	}

	protected String getCurrentURI() {
		String uri = getRequest().getRequestURI();
		if (getRequest().getQueryString() != null)
			uri += "?" + getRequest().getQueryString();
		return uri;
	}
}
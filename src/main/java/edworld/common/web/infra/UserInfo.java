package edworld.common.web.infra;

import java.util.Collection;

public class UserInfo {
	private Integer id;
	private String name;
	private String profileURL;
	private String pictureURL;
	private String gender;
	private String locale;
	private String email;
	private String accountProvider;
	private String accountId;
	private String accountToken;
	private String accountRefreshToken;
	private boolean admin;
	private Collection<String> roles;

	public UserInfo(Integer id, String name, String profileURL, String pictureURL, String gender, String locale,
			String email, String accountProvider, String accountId, String accountToken, String accountRefreshToken,
			boolean admin, Collection<String> roles) {
		this.id = id;
		this.name = name;
		this.profileURL = profileURL;
		this.pictureURL = pictureURL;
		this.gender = gender;
		this.locale = locale;
		this.email = email;
		this.accountProvider = accountProvider;
		this.accountId = accountId;
		this.accountToken = accountToken;
		this.accountRefreshToken = accountRefreshToken;
		this.admin = admin;
		this.roles = roles;
	}

	public Integer getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getProfileURL() {
		return profileURL;
	}

	public String getPictureURL() {
		return pictureURL;
	}

	public String getGender() {
		return gender;
	}

	public String getLocale() {
		return locale;
	}

	public String getEmail() {
		return email;
	}

	public String getAccountProvider() {
		return accountProvider;
	}

	public String getAccountId() {
		return accountId;
	}

	public String getAccountToken() {
		return accountToken;
	}

	public String getAccountRefreshToken() {
		return accountRefreshToken;
	}

	public boolean isAdmin() {
		return admin;
	}

	public boolean isInRole(String roleName) {
		for (String role : roles)
			if (role.equalsIgnoreCase(roleName))
				return true;
		return isAdmin();
	}
}

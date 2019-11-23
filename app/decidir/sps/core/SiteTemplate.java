package decidir.sps.core;

public class SiteTemplate {

	private String site_id;
	private Long template_id;
	private String alias;
	private boolean signed;
	private String template;
	private int state;
	
	public SiteTemplate(String site_id, Long template_id, String alias, boolean signed, String template, int state) {
		super();
		this.site_id = site_id;
		this.template_id = template_id;
		this.alias = alias;
		this.signed = signed;
		this.template = template;
		this.state = state;
	}

	public String getSite_id() {
		return site_id;
	}

	public void setSite_id(String site_id) {
		this.site_id = site_id;
	}

	public Long getTemplate_id() {
		return template_id;
	}

	public void setTemplate_id(Long template_id) {
		this.template_id = template_id;
	}

	public String getAlias() {
		return alias;
	}

	public void setAlias(String alias) {
		this.alias = alias;
	}

	public boolean isSigned() {
		return signed;
	}

	public void setSigned(boolean signed) {
		this.signed = signed;
	}

	public String getTemplate() { return template; }

	public void setTemplate(String template) { this.template = template; }

	public int getState() { return state; }

	public void setState(int state) { this.state = state; }
	
}

package decidir.sps.core;

public class SitePaymentType {

    private String siteId;
    private String paymentType;

    public SitePaymentType() {
    }

    public SitePaymentType(String siteId, String paymentType) {
        this.siteId = siteId;
        this.paymentType = paymentType;
    }

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }
}

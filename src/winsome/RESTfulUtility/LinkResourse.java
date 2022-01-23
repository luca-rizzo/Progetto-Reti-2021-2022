package winsome.RESTfulUtility;

public class LinkResourse {
    //URI di riferimento alla risorsa rel
    private String href;
    //risorsa di riferimento
    private String rel;
    //descrizione dettagliata del riferimento
    private String description;

    public LinkResourse(String href, String rel, String description) {
        this.href = href;
        this.rel = rel;
        this.description = description;
    }

    //*****GETTERS AND SETTER*****//
    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public String getRel() {
        return rel;
    }

    public void setRel(String rel) {
        this.rel = rel;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

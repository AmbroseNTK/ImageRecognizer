package ntk.ambrose.imagerecognizer;

public class Food {
    private String predictName;
    private String name;
    private String link;
    private String description;


    public Food(String predictName, String name, String link, String description) {
        this.predictName = predictName;
        this.name = name;
        this.link=link;
        this.description = description;
    }

    public String getPredictName() {
        return predictName;
    }

    public void setPredictName(String predictName) {
        this.predictName = predictName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}

package dev.xerohero.protracker;

public class SavedFilter {
    private String name;
    private String status;
    private String priority;
    private String milestone;
    private String textSearch;

    public SavedFilter() {}

    public SavedFilter(String name, String status, String priority, String milestone, String textSearch) {
        this.name = name;
        this.status = status;
        this.priority = priority;
        this.milestone = milestone;
        this.textSearch = textSearch;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getMilestone() { return milestone; }
    public void setMilestone(String milestone) { this.milestone = milestone; }

    public String getTextSearch() { return textSearch; }
    public void setTextSearch(String textSearch) { this.textSearch = textSearch; }

    @Override
    public String toString() { return name; }
}

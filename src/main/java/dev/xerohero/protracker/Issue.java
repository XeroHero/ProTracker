package dev.xerohero.protracker;

public class Issue {
    private String title;
    private String milestone;
    private String status;   // "OPEN", "IN_PROGRESS", "CLOSED"
    private String priority; // "LOW", "MEDIUM", "HIGH"
    private String description;

    public Issue() {}

    public Issue(String title, String milestone, String status, String priority, String description) {
        this.title = title;
        this.milestone = milestone;
        this.status = status;
        this.priority = priority;
        this.description = description;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMilestone() { return milestone; }
    public void setMilestone(String milestone) { this.milestone = milestone; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

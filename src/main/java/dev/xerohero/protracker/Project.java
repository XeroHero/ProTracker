package dev.xerohero.protracker;

import dev.xerohero.protracker.Issue;

import java.util.ArrayList;
import java.util.List;

public class Project {
    private String name;
    private List<Issue> issues = new ArrayList<>();
    private List<SavedFilter> savedFilters = new ArrayList<>();

    public Project() {}
    public Project(String name) { this.name = name; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<Issue> getIssues() { return issues; }
    public void setIssues(List<Issue> issues) { this.issues = issues; }

    public List<SavedFilter> getSavedFilters() { return savedFilters; }
    public void setSavedFilters(List<SavedFilter> savedFilters) { this.savedFilters = savedFilters; }
}

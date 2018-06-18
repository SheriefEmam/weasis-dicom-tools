package org.weasis.dicom.param;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class ForwardDicomNode extends DicomNode {
    public static final int MAX_IDLE_CONNECTION = 15;

    private final ScheduledThreadPoolExecutor checkProcess;
    private final String forwardAETitle;
    private final Set<DicomNode> acceptedSourceNodes;
    private volatile long activityTimestamp;

    public ForwardDicomNode(String fwdAeTitle) {
        this(fwdAeTitle, null);
    }

    public ForwardDicomNode(String fwdAeTitle, String fwdHostname) {
        super(fwdAeTitle, fwdHostname, null);
        this.forwardAETitle = Objects.requireNonNull(fwdAeTitle, "fwdAeTitle must be unique and non null!");
        this.acceptedSourceNodes = new HashSet<>();
        this.checkProcess = new ScheduledThreadPoolExecutor(1);
        this.activityTimestamp = 0;
    }

    public void addAcceptedSourceNode(String srcAeTitle) {
        addAcceptedSourceNode(srcAeTitle, null, false);
    }

    public void addAcceptedSourceNode(String srcAeTitle, String srcHostname, boolean validateSrcHostname) {
        acceptedSourceNodes.add(new DicomNode(srcAeTitle, srcHostname, null, validateSrcHostname));
    }

    public Set<DicomNode> getAcceptedSourceNodes() {
        return acceptedSourceNodes;
    }

    public long getActivityTimestamp() {
        return activityTimestamp;
    }

    public void setActivityTimestamp(long timestamp) {
        this.activityTimestamp = timestamp;
    }

    public ScheduledThreadPoolExecutor getCheckProcess() {
        return checkProcess;
    }

    public String getForwardAETitle() {
        return forwardAETitle;
    }

    @Override
    public String toString() {
        return forwardAETitle;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + forwardAETitle.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (getClass() != obj.getClass())
            return false;
        ForwardDicomNode other = (ForwardDicomNode) obj;
        return forwardAETitle.equals(other.forwardAETitle);
    }

}

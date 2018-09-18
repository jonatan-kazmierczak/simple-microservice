package demo;

import java.util.Collection;
import java.util.LinkedList;

public class ApiResponse {
    public long startTimestamp;
    public long duration;
    public Collection<String> body = new LinkedList<>();


    public void setEndTimestamp(long endTimestamp) {
        this.duration = endTimestamp - startTimestamp;
    }

    public long getDuration() { return duration; }

    @Override
    public String toString() {
        return "ApiResponse{" +
                "startTimestamp=" + startTimestamp +
                ", duration=" + duration +
                //", body=" + body +
                '}';
    }
}

package ro.aciee.advisor.domain.group.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PendingRequests {
    private List<JoinRequest> requests;
    private long members;
    private int maxSize;
}

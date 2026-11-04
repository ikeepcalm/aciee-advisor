package ro.aciee.advisor.domain.moderation.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class Queue {
    private long total;
    private List<QueueItem> items;
}

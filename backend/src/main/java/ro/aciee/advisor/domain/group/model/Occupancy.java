package ro.aciee.advisor.domain.group.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Occupancy {
    private long members;
    private int maxSize;
}

package ro.aciee.advisor.domain.group.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ro.aciee.advisor.domain.group.model.CreatedInvite;
import ro.aciee.advisor.domain.group.model.GroupSummary;
import ro.aciee.advisor.domain.group.model.Invite;
import ro.aciee.advisor.domain.group.model.InviteBody;
import ro.aciee.advisor.domain.group.model.InvitePreview;
import ro.aciee.advisor.domain.group.model.JoinBody;
import ro.aciee.advisor.domain.group.model.MyGroups;
import ro.aciee.advisor.domain.group.model.PendingRequests;
import ro.aciee.advisor.domain.group.model.RosterEntry;
import ro.aciee.advisor.domain.group.service.GroupService;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @GetMapping
    public List<GroupSummary> currentGroups() {
        return groupService.currentGroups();
    }

    @GetMapping("/mine")
    public MyGroups mine() {
        return groupService.mine();
    }

    @GetMapping("/invites/{token}")
    public InvitePreview previewInvite(@PathVariable String token) {
        return groupService.previewInvite(token);
    }

    @PostMapping("/join")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void join(@Valid @RequestBody JoinBody body) {
        groupService.join(body.getToken(), body.getSubgroupId());
    }

    @PostMapping("/{groupId}/leader-claims")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void claimLeadership(@PathVariable int groupId) {
        groupService.claimLeadership(groupId);
    }

    @GetMapping("/{groupId}/requests")
    public PendingRequests pendingRequests(@PathVariable int groupId) {
        return groupService.pendingRequests(groupId);
    }

    @GetMapping("/{groupId}/members")
    public List<RosterEntry> roster(@PathVariable int groupId) {
        return groupService.roster(groupId);
    }

    @PostMapping("/requests/{requestId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable int requestId) {
        groupService.approve(requestId);
    }

    @PostMapping("/requests/{requestId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable int requestId) {
        groupService.reject(requestId);
    }

    @GetMapping("/{groupId}/invites")
    public List<Invite> invites(@PathVariable int groupId) {
        return groupService.invites(groupId);
    }

    @PostMapping("/{groupId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedInvite createInvite(@PathVariable int groupId, @Valid @RequestBody InviteBody body) {
        return groupService.createInvite(groupId, body.getDays(), body.getMaxUses());
    }

    @PatchMapping("/invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateInvite(@PathVariable int inviteId) {
        groupService.deactivateInvite(inviteId);
    }
}

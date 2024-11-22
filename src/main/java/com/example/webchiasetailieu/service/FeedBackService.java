package com.example.webchiasetailieu.service;

import com.example.webchiasetailieu.dto.request.FeedBackRequest;
import com.example.webchiasetailieu.dto.request.HandleFeedbackRequest;
import com.example.webchiasetailieu.dto.request.NotificationCreationRequest;
import com.example.webchiasetailieu.dto.request.UpdateFeedbackRequest;
import com.example.webchiasetailieu.dto.response.FeedBackResponse;
import com.example.webchiasetailieu.dto.response.NotificationResponse;
import com.example.webchiasetailieu.entity.Account;
import com.example.webchiasetailieu.entity.Documents;
import com.example.webchiasetailieu.entity.Feedbacks;
import com.example.webchiasetailieu.enums.FeedbackType;
import com.example.webchiasetailieu.enums.NotificationType;
import com.example.webchiasetailieu.enums.StatusFeedbackType;
import com.example.webchiasetailieu.exception.AppException;
import com.example.webchiasetailieu.exception.ErrorCode;
import com.example.webchiasetailieu.repository.DocumentRepository;
import com.example.webchiasetailieu.repository.FeedBackRepository;
import com.example.webchiasetailieu.repository.NotificationRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FeedBackService {
    FeedBackRepository repository;
    AccountService accountService;
    NotificationService notificationService;
    private final DocumentRepository documentRepository;
    private final NotificationRepository notificationRepository;

    @PreAuthorize("hasRole('USER')")
    public FeedBackResponse createFeedback(FeedBackRequest request) {
        String type;
        Account account = accountService.getAccountFromAuthentication();

        if (!isValidFeedbackType(request.getFeedbackType().toString()))
            throw new AppException(ErrorCode.FEEDBACK_TYPE_INCORRECT);

        type = switch (request.getFeedbackType()) {
            case ANOTHER_PROBLEM -> "Another problem";
            case REPORT_DOCUMENT -> "Violating content of documents";
        };

        Feedbacks feedbacks = Feedbacks.builder()
                .feedback(request.getFeedback())
                .type(type)
                .status(StatusFeedbackType.UNPROCESSED.getDescription())
                .account(account)
                .otherId(request.getOtherId())
                .build();
        return convertToResponse(repository.save(feedbacks));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public FeedBackResponse updateStatusFeedbackOrResponseFromAdmin(UpdateFeedbackRequest request) {
        Feedbacks feedbacks = repository.findById(request.getId()).orElseThrow(
                () -> new AppException(ErrorCode.FEEDBACK_NOT_FOUND));
        if(request.getStatus() != null)
            feedbacks.setStatus(request.getStatus());
        if(request.getResponseFromAdmin()!= null)
            feedbacks.setFeedbackFromAdmin(request.getResponseFromAdmin());
        return convertToResponse(repository.save(feedbacks));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public NotificationResponse violationNotification(HandleFeedbackRequest request) {
        Documents documents = documentRepository.findById(request.getDocId())
                                .orElseThrow(() -> new AppException(ErrorCode.DOC_NOT_EXIST));

        return notificationService.notify(NotificationCreationRequest.builder()
                    .type(NotificationType.POST_VIOLATION)
                    .accountId(documents.getCreatedBy().getId())
                .build());
    }

    @PreAuthorize("hasRole('USER')")
    public List<Feedbacks> getMyFeedbacks() {
        return repository.findAllByAccount_Id(accountService.getAccountFromAuthentication().getId());
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<Feedbacks> getAll() {
        if(repository.findAll().isEmpty())
            throw new AppException(ErrorCode.LIST_EMPTY);
        return repository.findAll();
    }

    @PreAuthorize("hasAuthority('GET_FEEDBACK')")
    public FeedBackResponse getById(String id) {
        return convertToResponse(repository.findById(id).orElseThrow(
                () -> new AppException(ErrorCode.NOTIFICATION_NOT_EXISTED)));
    }

    @PreAuthorize("hasAuthority('DELETE_FEEDBACK')")
    public String deleteById(String id) {
        repository.deleteById(id);
        return "Deleted";
    }

    private FeedBackResponse convertToResponse(Feedbacks feedbacks) {
        return FeedBackResponse.builder()
                .email(feedbacks.getAccount().getEmail())
                .feedback(feedbacks.getFeedback())
                .date(feedbacks.getDate())
                .type(feedbacks.getType())
                .status(feedbacks.getStatus())
                .feedbackFromAdmin(feedbacks.getFeedbackFromAdmin())
                .otherId(feedbacks.getOtherId())
                .build();
    }

    private boolean isValidFeedbackType(String input) {
        for (FeedbackType type : FeedbackType.values()) {
            if (type.name().equals(input)) {
                return true;
            }
        }
        return false;
    }
}

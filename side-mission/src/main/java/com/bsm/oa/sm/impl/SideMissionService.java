package com.bsm.oa.sm.impl;

import static com.bsm.oa.common.util.CollectionUtils.mapList;
import static com.bsm.oa.common.util.CollectionUtils.partition;
import static com.bsm.oa.common.util.CollectionUtils.streamOfNullable;
import static com.bsm.oa.common.util.PageUtils.retrievePage;
import static com.bsm.oa.sm.exception.SideMissionException.REQUIRED_PROOF_TYPES_NOT_MATCHED;
import static com.bsm.oa.sm.exception.SideMissionException.SIDE_MISSION_INVALID_RATER;
import static com.bsm.oa.sm.exception.SideMissionException.SIDE_MISSION_PARAM_MISSING;
import static com.bsm.oa.sm.exception.SideMissionException.SIDE_MISSION_PARAM_UNKNOWN;
import static com.bsm.oa.sm.exception.SideMissionException.SIDE_MISSION_REPORT_NOT_EXISTS;
import static com.bsm.oa.sm.exception.SideMissionException.SIDE_MISSION_REPORT_RATED;
import static com.bsm.oa.sm.exception.SideMissionException.SIDE_MISSION_TYPE_NOT_EXISTS;
import static com.bsm.oa.sm.model.ProofRequirementType.PHOTO_OR_VIDEO;
import static java.util.Optional.of;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import com.bsm.oa.common.cdi.BeanFactory;
import com.bsm.oa.common.model.UserId;
import com.bsm.oa.common.service.UserDetailsProvider;
import com.bsm.oa.sm.annotation.PerformParamValidator.Literal;
import com.bsm.oa.sm.dao.SideMissionRepository;
import com.bsm.oa.sm.model.PerformParam;
import com.bsm.oa.sm.model.PerformParamSymbol;
import com.bsm.oa.sm.model.ProofMediaLink;
import com.bsm.oa.sm.model.RaterType;
import com.bsm.oa.sm.model.SideMissionReport;
import com.bsm.oa.sm.model.SideMissionReportFilter;
import com.bsm.oa.sm.model.SideMissionReportId;
import com.bsm.oa.sm.model.SideMissionType;
import com.bsm.oa.sm.model.SideMissionTypeID;
import com.bsm.oa.sm.request.ReportSideMissionRequest;
import com.bsm.oa.sm.service.IPerformParamValidator;
import com.bsm.oa.sm.service.ISideMissionService;
import com.bsm.oa.user.service.UserService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class SideMissionService implements ISideMissionService {

  private final SideMissionRepository sideMissionRepository;
  private final UserDetailsProvider userDetailsProvider;
  private final UserService userService;
  private final BeanFactory beanFactory;

  @Override
  @Transactional
  public void mergeSideMissionType(@Valid @NotNull SideMissionType missionType) {
    sideMissionRepository.mergeSideMissionType(missionType);
  }

  @Override
  public List<SideMissionType> getSideMissionTypes() {
    return sideMissionRepository.getSideMissionTypes();
  }

  @Override
  public SideMissionType getSideMissionType(@Valid @NotNull SideMissionTypeID typeId) {
    return of(typeId).map(sideMissionRepository::getSideMissionType)
      .orElseThrow(SIDE_MISSION_TYPE_NOT_EXISTS);
  }

  @Override
  @Transactional
  public void reportSideMission(@Valid @NotNull ReportSideMissionRequest request) {
    userService.assertUserExists(request.getPerformingUserId(), "performing user");

    var missionType = of(request.getMissionTypeId())
      .map(sideMissionRepository::getSideMissionType)
      .orElseThrow(SIDE_MISSION_TYPE_NOT_EXISTS);

    validateProofMedia(missionType, request.getProofMediaLinks());

    request.setReportingUserId(userDetailsProvider.getUserId());
    sideMissionRepository.insertSideMissionReport(request);
  }

  @Override
  public Page<SideMissionReport> getSideMissionReports(@NotNull RaterType toRateBy,
                                                       @NotNull Pageable pageable) {
    var filter = SideMissionReportFilter.of(toRateBy, userDetailsProvider.getUserId());

    return retrievePage(filter, pageable, sideMissionRepository::selectReports,
      sideMissionRepository::selectReportsCount);
  }

  @Override
  public List<ProofMediaLink> getReportProofs(@Valid @NotNull SideMissionReportId reportId) {
    return sideMissionRepository.getReportProofs(reportId);
  }

  @Override
  public void rateReport(@Valid @NotNull SideMissionReportId reportId, @NotNull RaterType raterType,
                         @Valid @NotEmpty Map<PerformParamSymbol, Double> rates) {
    var report = of(reportId).map(sideMissionRepository::selectReport)
      .orElseThrow(SIDE_MISSION_REPORT_NOT_EXISTS);

    UserId userId = userDetailsProvider.getUserId();

    if (sideMissionRepository.hasRatedReport(userId, reportId)) {
      SIDE_MISSION_REPORT_RATED.raise();
    }
    validatePerformParams(raterType, report.getMissionTypeId(), new HashMap<>(rates));

    sideMissionRepository.insertReportRate(userId, reportId, rates);
  }

  private void validatePerformParams(@NotNull RaterType raterType,
                                     @Valid @NotNull SideMissionTypeID missionTypeId,
                                     @Valid @NotEmpty Map<PerformParamSymbol, Double> rates) {
    var missionType = sideMissionRepository.getSideMissionType(missionTypeId);

    var paramsPartition = partition(missionType.getPerformParams(),
      param -> raterType.equals(param.getToRateBy()));

    var expectedParams = paramsPartition.get(true);
    var unexpectedParams = paramsPartition.get(false);

    unexpectedParams.forEach(param ->
      of(param.getSymbol()).filter(rates::containsKey).ifPresent(symbol ->
        SIDE_MISSION_INVALID_RATER.raise(symbol + " -> " + raterType.getOther()))
    );

    expectedParams.forEach(param ->
      of(param.getSymbol()).map(rates::remove)
        .ifPresentOrElse(rateValue -> validateRateValue(param, rateValue),
          () -> SIDE_MISSION_PARAM_MISSING.raise(param.getSymbol()))
    );

    if (isNotEmpty(rates.values())) {
      SIDE_MISSION_PARAM_UNKNOWN.raise(rates.keySet());
    }
  }

  private void validateRateValue(@Valid @NotNull PerformParam param, @NotNull Double value) {
    var validator = beanFactory.getBean(IPerformParamValidator.class, new Literal(param.getType()));

    validator.validateRateValue(param, value);
  }

  private void validateProofMedia(@Valid @NotNull SideMissionType missionType,
                                  @Valid List<ProofMediaLink> proofMediaLinks) {

    var requiredProofTypes = stringProofTypes(missionType);

    long leftMediaTypes = streamOfNullable(proofMediaLinks)
      .map(ProofMediaLink::getType).map(Enum::name)
      .filter(provided -> !requiredProofTypes.remove(provided))
      .count();

    long requiredAnyMediaCount = requiredProofTypes.stream()
      .filter(type -> PHOTO_OR_VIDEO.name().equals(type)).count();

    long notMatchedRequiredSpecificMediaTypes = requiredProofTypes.size() - requiredAnyMediaCount;

    if (leftMediaTypes < requiredAnyMediaCount || notMatchedRequiredSpecificMediaTypes > 0) {

      String details = "Required: " + stringProofTypes(missionType)
        + ". Provided: " + stringProofTypes(proofMediaLinks);

      REQUIRED_PROOF_TYPES_NOT_MATCHED.raise(details);
    }
  }

  private List<String> stringProofTypes(@Valid @NotNull SideMissionType missionType) {
    return mapList(missionType.getProofRequirements(), x -> x.getType().name());
  }

  private List<String> stringProofTypes(@Valid List<ProofMediaLink> proofMediaLinks) {
    return mapList(proofMediaLinks, x -> x.getType().name());
  }

}

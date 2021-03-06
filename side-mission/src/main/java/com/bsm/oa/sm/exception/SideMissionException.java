package com.bsm.oa.sm.exception;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
public enum SideMissionException implements Supplier<RuntimeException> {

  SIDE_MISSION_TYPE_NOT_EXISTS("Side mission type does not exists."),
  REQUIRED_PROOF_TYPES_NOT_MATCHED("Provided proof resource types do not meet requirements. "),
  SIDE_MISSION_REPORT_NOT_EXISTS("Side mission report does not exists."),
  SIDE_MISSION_REPORT_RATED("User has already rated that side mission report."),
  SIDE_MISSION_INVALID_RATER("Parameter is intended to be rated by the other type of rater. "),
  SIDE_MISSION_PARAM_MISSING("Required side mission perform param not provided: "),
  SIDE_MISSION_PARAM_UNKNOWN("Unknown side mission perform params: "),
  SIDE_MISSION_PARAM_INVALID("Invalid side mission perform param rate value. ");

  private final String message;

  @Override
  public RuntimeException get() {
    return new ResponseStatusException(BAD_REQUEST, message);
  }

  public RuntimeException get(Object details) {
    return new ResponseStatusException(BAD_REQUEST, message + details);
  }

  public void raise(Object details) {
    throw get(details);
  }

  public void raise() {
    throw get();
  }
}

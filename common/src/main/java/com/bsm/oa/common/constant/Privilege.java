package com.bsm.oa.common.constant;

import org.springframework.security.core.GrantedAuthority;

public enum Privilege implements GrantedAuthority {

  //Allows user to access app resources stored in AWS (ex. photos & videos)
  PRV_AWS_RESOURCE_ACCESS,

  //Allows user to report side mission
  PRV_REPORT_SM,

  //Allows user to judge side missions
  PRV_JUDGE_RATE_SM,

  //Allows user to assign rates to side mission params requiring professor
  PRV_PROFESSOR_RATE_SM,

  //Allows user to add main competition points
  PRV_ADD_MC_POINTS,

  //Allows user to add bet points
  PRV_ADD_BET_POINTS,

  //Allows user to add medal points
  PRV_ADD_MEDAL_POINTS,

  //Allows user to manage other users
  PRV_EDIT_USERS,

  //Allows user to edit side missions
  PRV_EDIT_SM,

  //Allow user to manage master switch, which enables reporting and adding points
  PRV_MASTER_LOCK,
  ;

  @Override
  public String getAuthority() {
    return name();
  }
}

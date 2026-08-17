package com.netlink.onemep_feature.designimport.service;

import com.netlink.onemep_feature.design.model.WorkProgress;
import com.netlink.onemep_feature.lookup.model.LookupValue;

/**
 * A row that passed every rule, with its catalogue references already resolved and its Design
 * Number already generated.
 *
 * <p>Everything the writer needs to insert one Design, and nothing it would have to look up again —
 * the segments were resolved to build the number, so carrying them forward avoids a second round
 * trip per row.
 */
public record ValidatedRow(
    int rowNumber,
    String designNumber,
    String zoneCode,
    LookupValue discipline,
    LookupValue type,
    LookupValue subject,
    LookupValue floor,
    LookupValue stage,
    String title,
    String titleNormalized,
    String sheetSize,
    String scale,
    String preparedBy,
    WorkProgress workProgress) {}

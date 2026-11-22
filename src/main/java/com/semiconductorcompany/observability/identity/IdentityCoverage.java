package com.semiconductorcompany.observability.identity;

/** Success metric "assets with a resolved canonical identity": assets that two or more tools agree on. */
public record IdentityCoverage(int assets, long seenByTwoOrMoreTools, double ratio) {
}

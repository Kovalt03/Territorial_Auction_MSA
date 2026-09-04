package com.territorial.map.domain.map.event;

public record TerritoryLostEvent(Long territoryId, Long formerOwnerId) {}

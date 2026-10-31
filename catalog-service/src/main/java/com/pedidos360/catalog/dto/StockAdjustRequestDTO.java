package com.pedidos360.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** Cuerpo de POST /api/catalog/stock/reserve y /release (los usa order-service). */
@Data
public class StockAdjustRequestDTO {

    @NotEmpty(message = "Debe indicar al menos un item")
    @Valid
    private List<StockItemDTO> items;
}

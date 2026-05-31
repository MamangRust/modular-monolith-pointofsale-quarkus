package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.CashierDto;
import io.smallrye.mutiny.Uni;

public interface CashierService {
    Uni<CashierDto.ApiResponsePaginationCashier> listCashiers(int page, int size, String search);
    Uni<CashierDto.ApiResponseCashier> getCashier(int id);
    Uni<CashierDto.ApiResponsePaginationCashierDeleteAt> getActiveCashiers(int page, int size, String search);
    Uni<CashierDto.ApiResponsePaginationCashierDeleteAt> getTrashedCashiers(int page, int size, String search);
    Uni<CashierDto.ApiResponsePaginationCashier> getCashiersByMerchant(int merchantId, int page, int size, String search);
    Uni<CashierDto.ApiResponseCashier> createCashier(CashierDto.CreateRequest body);
    Uni<CashierDto.ApiResponseCashier> updateCashier(int id, CashierDto.UpdateRequest body);
    Uni<CashierDto.ApiResponseCashierDeleteAt> deleteCashier(int id);
    Uni<CashierDto.ApiResponseCashierDeleteAt> restoreCashier(int id);
    Uni<CashierDto.SimpleResponse> deleteCashierPermanent(int id);
    Uni<CashierDto.SimpleResponse> restoreAllCashier();
    Uni<CashierDto.SimpleResponse> deleteAllCashierPermanent();

    // Stats
    Uni<CashierDto.ApiResponseCashierMonthlyTotalSales> getMonthlyTotalSales(int year, int month);
    Uni<CashierDto.ApiResponseCashierYearlyTotalSales> getYearlyTotalSales(int year);
    Uni<CashierDto.ApiResponseCashierMonthlyTotalSales> getMonthlyTotalSalesById(int cashierId, int year, int month);
    Uni<CashierDto.ApiResponseCashierYearlyTotalSales> getYearlyTotalSalesById(int cashierId, int year);
    Uni<CashierDto.ApiResponseCashierMonthlyTotalSales> getMonthlyTotalSalesByMerchant(int merchantId, int year, int month);
    Uni<CashierDto.ApiResponseCashierYearlyTotalSales> getYearlyTotalSalesByMerchant(int merchantId, int year);
    
    Uni<CashierDto.ApiResponseCashierMonthSales> getMonthlySales(int year);
    Uni<CashierDto.ApiResponseCashierYearSales> getYearlySales(int year);
    Uni<CashierDto.ApiResponseCashierMonthSales> getMonthlySalesByMerchant(int merchantId, int year);
    Uni<CashierDto.ApiResponseCashierYearSales> getYearlySalesByMerchant(int merchantId, int year);
    Uni<CashierDto.ApiResponseCashierMonthSales> getMonthlySalesById(int cashierId, int year);
    Uni<CashierDto.ApiResponseCashierYearSales> getYearlySalesById(int cashierId, int year);
}

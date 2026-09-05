package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.CashierDto;
import com.sanedge.gateway.domain.requests.FindAllCashiersRequest;
import com.sanedge.gateway.domain.requests.FindCashiersByMerchantRequest;
import com.sanedge.gateway.domain.requests.GetCashierMonthlyTotalSalesRequest;
import com.sanedge.gateway.domain.requests.GetCashierMonthlyTotalSalesByMerchantRequest;
import io.smallrye.mutiny.Uni;

public interface CashierService {
    Uni<CashierDto.ApiResponsePaginationCashier> listCashiers(FindAllCashiersRequest request);
    Uni<CashierDto.ApiResponseCashier> getCashier(int id);
    Uni<CashierDto.ApiResponsePaginationCashierDeleteAt> getActiveCashiers(FindAllCashiersRequest request);
    Uni<CashierDto.ApiResponsePaginationCashierDeleteAt> getTrashedCashiers(FindAllCashiersRequest request);
    Uni<CashierDto.ApiResponsePaginationCashier> getCashiersByMerchant(FindCashiersByMerchantRequest request);
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
    Uni<CashierDto.ApiResponseCashierMonthlyTotalSales> getMonthlyTotalSalesById(GetCashierMonthlyTotalSalesRequest request);
    Uni<CashierDto.ApiResponseCashierYearlyTotalSales> getYearlyTotalSalesById(int cashierId, int year);
    Uni<CashierDto.ApiResponseCashierMonthlyTotalSales> getMonthlyTotalSalesByMerchant(GetCashierMonthlyTotalSalesByMerchantRequest request);
    Uni<CashierDto.ApiResponseCashierYearlyTotalSales> getYearlyTotalSalesByMerchant(int merchantId, int year);
    
    Uni<CashierDto.ApiResponseCashierMonthSales> getMonthlySales(int year);
    Uni<CashierDto.ApiResponseCashierYearSales> getYearlySales(int year);
    Uni<CashierDto.ApiResponseCashierMonthSales> getMonthlySalesByMerchant(int merchantId, int year);
    Uni<CashierDto.ApiResponseCashierYearSales> getYearlySalesByMerchant(int merchantId, int year);
    Uni<CashierDto.ApiResponseCashierMonthSales> getMonthlySalesById(int cashierId, int year);
    Uni<CashierDto.ApiResponseCashierYearSales> getYearlySalesById(int cashierId, int year);
}

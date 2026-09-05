package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.TransactionDto;
import com.sanedge.gateway.domain.requests.FindAllTransactionsRequest;
import com.sanedge.gateway.domain.requests.FindTransactionsByCardNumberRequest;
import com.sanedge.gateway.domain.requests.GetTransactionStatusFailedByCardNumberRequest;
import com.sanedge.gateway.domain.requests.GetTransactionStatusSuccessByCardNumberRequest;
import io.smallrye.mutiny.Uni;

public interface TransactionService {
    Uni<TransactionDto.ApiResponsePaginationTransaction> listTransactions(FindAllTransactionsRequest request);
    Uni<TransactionDto.ApiResponsePaginationTransaction> listTransactionsByCardNumber(FindTransactionsByCardNumberRequest request);
    Uni<TransactionDto.ApiResponseTransaction> getTransaction(int id);
    Uni<TransactionDto.ApiResponseTransactions> getTransactionsByMerchant(int merchantId);
    Uni<TransactionDto.ApiResponsePaginationTransactionDeleteAt> getActiveTransactions(FindAllTransactionsRequest request);
    Uni<TransactionDto.ApiResponsePaginationTransactionDeleteAt> getTrashedTransactions(FindAllTransactionsRequest request);
    Uni<TransactionDto.ApiResponseTransaction> createTransaction(TransactionDto.CreateRequest body);
    Uni<TransactionDto.ApiResponseTransaction> updateTransaction(int id, TransactionDto.UpdateRequest body);
    Uni<TransactionDto.ApiResponseTransactionDeleteAt> deleteTransaction(int id);
    Uni<TransactionDto.ApiResponseTransactionDeleteAt> restoreTransaction(int id);
    Uni<TransactionDto.SimpleResponse> deleteTransactionPermanent(int id);
    Uni<TransactionDto.SimpleResponse> restoreAllTransaction();
    Uni<TransactionDto.SimpleResponse> deleteAllTransactionPermanent();

    // Stats Amount
    Uni<TransactionDto.ApiResponseTransactionMonthAmount> getMonthlyAmounts(int year);
    Uni<TransactionDto.ApiResponseTransactionYearAmount> getYearlyAmounts(int year);
    Uni<TransactionDto.ApiResponseTransactionMonthAmount> getMonthlyAmountsByCardNumber(String cardNumber, int year);
    Uni<TransactionDto.ApiResponseTransactionYearAmount> getYearlyAmountsByCardNumber(String cardNumber, int year);

    // Stats Method
    Uni<TransactionDto.ApiResponseTransactionMonthMethod> getMonthlyPaymentMethods(int year);
    Uni<TransactionDto.ApiResponseTransactionYearMethod> getYearlyPaymentMethods(int year);
    Uni<TransactionDto.ApiResponseTransactionMonthMethod> getMonthlyPaymentMethodsByCardNumber(String cardNumber, int year);
    Uni<TransactionDto.ApiResponseTransactionYearMethod> getYearlyPaymentMethodsByCardNumber(String cardNumber, int year);

    // Stats Status
    Uni<TransactionDto.ApiResponseTransactionMonthStatusSuccess> getMonthlyTransactionStatusSuccess(int year, int month);
    Uni<TransactionDto.ApiResponseTransactionYearStatusSuccess> getYearlyTransactionStatusSuccess(int year);
    Uni<TransactionDto.ApiResponseTransactionMonthStatusFailed> getMonthlyTransactionStatusFailed(int year, int month);
    Uni<TransactionDto.ApiResponseTransactionYearStatusFailed> getYearlyTransactionStatusFailed(int year);
    Uni<TransactionDto.ApiResponseTransactionMonthStatusSuccess> getMonthlyTransactionStatusSuccessByCardNumber(GetTransactionStatusSuccessByCardNumberRequest request);
    Uni<TransactionDto.ApiResponseTransactionYearStatusSuccess> getYearlyTransactionStatusSuccessByCardNumber(String cardNumber, int year);
    Uni<TransactionDto.ApiResponseTransactionMonthStatusFailed> getMonthlyTransactionStatusFailedByCardNumber(GetTransactionStatusFailedByCardNumberRequest request);
    Uni<TransactionDto.ApiResponseTransactionYearStatusFailed> getYearlyTransactionStatusFailedByCardNumber(String cardNumber, int year);
}

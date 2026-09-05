package com.sanedge.category.handler;

import com.sanedge.category.domain.requests.MonthTotalPrice;
import com.sanedge.category.domain.requests.MonthTotalPriceCategory;
import com.sanedge.category.domain.requests.MonthTotalPriceMerchant;
import com.sanedge.category.domain.requests.YearTotalPriceCategory;
import com.sanedge.category.domain.requests.YearTotalPriceMerchant;
import com.sanedge.category.service.stats.CategoryTotalPriceService;
import com.sanedge.category.service.statsbyid.CategoryTotalPriceByIdService;
import com.sanedge.category.service.statsbymerchant.CategoryTotalPriceByMerchantService;

import com.sanedge.common.grpc.GrpcErrorMapper;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.category.Category.FindYearMonthTotalPrices;
import pb.category.Category.FindYearMonthTotalPriceById;
import pb.category.Category.FindYearMonthTotalPriceByMerchant;
import pb.category.Category.FindYearTotalPrices;
import pb.category.Category.FindYearTotalPriceById;
import pb.category.Category.FindYearTotalPriceByMerchant;
import pb.category.Category.ApiResponseCategoryMonthlyTotalPrice;
import pb.category.Category.ApiResponseCategoryYearlyTotalPrice;
import pb.category.stats.MutinyCategoryTotalPriceServiceGrpc;

@GrpcService
@Singleton
public class CategoryTotalPriceGrpcHandler extends MutinyCategoryTotalPriceServiceGrpc.CategoryTotalPriceServiceImplBase {

    @Inject
    CategoryTotalPriceService categoryTotalPriceService;

    @Inject
    CategoryTotalPriceByIdService categoryTotalPriceByIdService;

    @Inject
    CategoryTotalPriceByMerchantService categoryTotalPriceByMerchantService;

    @Override
    public Uni<ApiResponseCategoryMonthlyTotalPrice> findMonthlyTotalPrices(FindYearMonthTotalPrices request) {
        MonthTotalPrice domainReq = new MonthTotalPrice();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());

        return categoryTotalPriceService.findMonthlyTotalPrice(domainReq)
                .map(apiResp -> {
                    ApiResponseCategoryMonthlyTotalPrice.Builder builder = ApiResponseCategoryMonthlyTotalPrice.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoMonthlyTotalPrice(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseCategoryYearlyTotalPrice> findYearlyTotalPrices(FindYearTotalPrices request) {
        return categoryTotalPriceService.findYearlyTotalPrice(request.getYear())
                .map(apiResp -> {
                    ApiResponseCategoryYearlyTotalPrice.Builder builder = ApiResponseCategoryYearlyTotalPrice.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoYearlyTotalPrice(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseCategoryMonthlyTotalPrice> findMonthlyTotalPricesById(FindYearMonthTotalPriceById request) {
        MonthTotalPriceCategory domainReq = new MonthTotalPriceCategory();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());
        domainReq.setCategoryId(request.getCategoryId());

        return categoryTotalPriceByIdService.findMonthlyTotalPriceById(domainReq)
                .map(apiResp -> {
                    ApiResponseCategoryMonthlyTotalPrice.Builder builder = ApiResponseCategoryMonthlyTotalPrice.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoMonthlyTotalPrice(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseCategoryYearlyTotalPrice> findYearlyTotalPricesById(FindYearTotalPriceById request) {
        YearTotalPriceCategory domainReq = new YearTotalPriceCategory();
        domainReq.setYear(request.getYear());
        domainReq.setCategoryId(request.getCategoryId());

        return categoryTotalPriceByIdService.findYearlyTotalPriceById(domainReq)
                .map(apiResp -> {
                    ApiResponseCategoryYearlyTotalPrice.Builder builder = ApiResponseCategoryYearlyTotalPrice.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoYearlyTotalPrice(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseCategoryMonthlyTotalPrice> findMonthlyTotalPricesByMerchant(FindYearMonthTotalPriceByMerchant request) {
        MonthTotalPriceMerchant domainReq = new MonthTotalPriceMerchant();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());
        domainReq.setMerchantId(request.getMerchantId());

        return categoryTotalPriceByMerchantService.findMonthlyTotalPriceByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseCategoryMonthlyTotalPrice.Builder builder = ApiResponseCategoryMonthlyTotalPrice.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoMonthlyTotalPrice(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseCategoryYearlyTotalPrice> findYearlyTotalPricesByMerchant(FindYearTotalPriceByMerchant request) {
        YearTotalPriceMerchant domainReq = new YearTotalPriceMerchant();
        domainReq.setYear(request.getYear());
        domainReq.setMerchantId(request.getMerchantId());

        return categoryTotalPriceByMerchantService.findYearlyTotalPriceByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseCategoryYearlyTotalPrice.Builder builder = ApiResponseCategoryYearlyTotalPrice.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoYearlyTotalPrice(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    private pb.category.Category.CategoriesMonthlyTotalPriceResponse toProtoMonthlyTotalPrice(
            com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse r) {
        if (r == null) {
            return pb.category.Category.CategoriesMonthlyTotalPriceResponse.getDefaultInstance();
        }
        return pb.category.Category.CategoriesMonthlyTotalPriceResponse.newBuilder()
                .setYear(r.getYear())
                .setMonth(r.getMonth())
                .setTotalRevenue(r.getTotalRevenue().intValue())
                .build();
    }

    private pb.category.Category.CategoriesYearlyTotalPriceResponse toProtoYearlyTotalPrice(
            com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse r) {
        if (r == null) {
            return pb.category.Category.CategoriesYearlyTotalPriceResponse.getDefaultInstance();
        }
        return pb.category.Category.CategoriesYearlyTotalPriceResponse.newBuilder()
                .setYear(r.getYear())
                .setTotalRevenue(r.getTotalRevenue().intValue())
                .build();
    }
}

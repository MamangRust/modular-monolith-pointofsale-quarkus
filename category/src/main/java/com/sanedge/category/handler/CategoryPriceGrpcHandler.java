package com.sanedge.category.handler;

import com.sanedge.category.domain.requests.MonthPriceId;
import com.sanedge.category.domain.requests.MonthPriceMerchant;
import com.sanedge.category.domain.requests.YearPriceId;
import com.sanedge.category.domain.requests.YearPriceMerchant;
import com.sanedge.category.service.stats.CategoryPriceService;
import com.sanedge.category.service.statsbyid.CategoryPriceByIdService;
import com.sanedge.category.service.statsbymerchant.CategoryPriceByMerchantService;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.category.Category.FindYearCategory;
import pb.category.Category.FindYearCategoryById;
import pb.category.Category.FindYearCategoryByMerchant;
import pb.category.Category.ApiResponseCategoryMonthPrice;
import pb.category.Category.ApiResponseCategoryYearPrice;
import pb.category.stats.MutinyCategoryPriceServiceGrpc;

@GrpcService
@Singleton
public class CategoryPriceGrpcHandler extends MutinyCategoryPriceServiceGrpc.CategoryPriceServiceImplBase {

    @Inject
    CategoryPriceService categoryPriceService;

    @Inject
    CategoryPriceByIdService categoryPriceByIdService;

    @Inject
    CategoryPriceByMerchantService categoryPriceByMerchantService;

    @Override
    public Uni<ApiResponseCategoryMonthPrice> findMonthPrice(FindYearCategory request) {
        return categoryPriceService.findMonthPrice(request.getYear())
                .map(apiResp -> {
                    ApiResponseCategoryMonthPrice.Builder builder = ApiResponseCategoryMonthPrice.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoMonthPrice(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseCategoryYearPrice> findYearPrice(FindYearCategory request) {
        return categoryPriceService.findYearPrice(request.getYear())
                .map(apiResp -> {
                    ApiResponseCategoryYearPrice.Builder builder = ApiResponseCategoryYearPrice.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoYearPrice(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseCategoryMonthPrice> findMonthPriceByMerchant(FindYearCategoryByMerchant request) {
        MonthPriceMerchant domainReq = new MonthPriceMerchant();
        domainReq.setYear(request.getYear());
        domainReq.setMerchantId(request.getMerchantId());

        return categoryPriceByMerchantService.findMonthPriceByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseCategoryMonthPrice.Builder builder = ApiResponseCategoryMonthPrice.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoMonthPrice(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseCategoryYearPrice> findYearPriceByMerchant(FindYearCategoryByMerchant request) {
        YearPriceMerchant domainReq = new YearPriceMerchant();
        domainReq.setYear(request.getYear());
        domainReq.setMerchantId(request.getMerchantId());

        return categoryPriceByMerchantService.findYearPriceByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseCategoryYearPrice.Builder builder = ApiResponseCategoryYearPrice.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoYearPrice(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseCategoryMonthPrice> findMonthPriceById(FindYearCategoryById request) {
        MonthPriceId domainReq = new MonthPriceId();
        domainReq.setYear(request.getYear());
        domainReq.setCategoryId(request.getCategoryId());

        return categoryPriceByIdService.findMonthPriceById(domainReq)
                .map(apiResp -> {
                    ApiResponseCategoryMonthPrice.Builder builder = ApiResponseCategoryMonthPrice.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoMonthPrice(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseCategoryYearPrice> findYearPriceById(FindYearCategoryById request) {
        YearPriceId domainReq = new YearPriceId();
        domainReq.setYear(request.getYear());
        domainReq.setCategoryId(request.getCategoryId());

        return categoryPriceByIdService.findYearPriceById(domainReq)
                .map(apiResp -> {
                    ApiResponseCategoryYearPrice.Builder builder = ApiResponseCategoryYearPrice.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoYearPrice(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    private pb.category.Category.CategoryMonthPriceResponse toProtoMonthPrice(
            com.sanedge.category.domain.response.CategoriesMonthPriceResponse r) {
        if (r == null) {
            return pb.category.Category.CategoryMonthPriceResponse.getDefaultInstance();
        }
        return pb.category.Category.CategoryMonthPriceResponse.newBuilder()
                .setMonth(r.getMonth())
                .setCategoryId(r.getCategoryId())
                .setCategoryName(r.getCategoryName())
                .setOrderCount(r.getOrderCount())
                .setItemsSold(r.getItemsSold())
                .setTotalRevenue(r.getTotalRevenue().intValue())
                .build();
    }

    private pb.category.Category.CategoryYearPriceResponse toProtoYearPrice(
            com.sanedge.category.domain.response.CategoriesYearPriceResponse r) {
        if (r == null) {
            return pb.category.Category.CategoryYearPriceResponse.getDefaultInstance();
        }
        return pb.category.Category.CategoryYearPriceResponse.newBuilder()
                .setYear(r.getYear())
                .setCategoryId(r.getCategoryId())
                .setCategoryName(r.getCategoryName())
                .setOrderCount(r.getOrderCount())
                .setItemsSold(r.getItemsSold())
                .setTotalRevenue(r.getTotalRevenue().intValue())
                .setUniqueProductsSold(r.getUniqueProductsSold())
                .build();
    }
}

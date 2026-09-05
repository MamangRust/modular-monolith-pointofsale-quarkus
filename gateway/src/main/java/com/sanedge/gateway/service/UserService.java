package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.UserDto;
import com.sanedge.gateway.domain.requests.FindAllUsersRequest;
import io.smallrye.mutiny.Uni;

public interface UserService {
    Uni<UserDto.ApiResponsePaginationUser> listUsers(FindAllUsersRequest request);
    Uni<UserDto.ApiResponsePaginationUserDeleteAt> getActiveUsers(FindAllUsersRequest request);
    Uni<UserDto.ApiResponsePaginationUserDeleteAt> getTrashedUsers(FindAllUsersRequest request);
    Uni<UserDto.ApiResponseUser> getUser(int id);
    Uni<UserDto.ApiResponseUser> createUser(UserDto.CreateRequest body);
    Uni<UserDto.ApiResponseUser> updateUser(int id, UserDto.UpdateRequest body);
    Uni<UserDto.ApiResponseUserDeleteAt> deleteUser(int id);
    Uni<UserDto.ApiResponseUserDeleteAt> restoreUser(int id);
    Uni<UserDto.SimpleResponse> deleteUserPermanent(int id);
    Uni<UserDto.SimpleResponse> restoreAllUser();
    Uni<UserDto.SimpleResponse> deleteAllUserPermanent();
}

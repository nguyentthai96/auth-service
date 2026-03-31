# Authentication/Authorization RBAC & ABAC/PBAC APIs


### 2. User Management (RBAC) APIs

#### 2.1 Lấy Danh Sách Người Dùng

- Endpoint: GET /api/users?page=1&size=10&sort=createdAt,desc&paginationType=page
- Request Parameters:
  - `page`: Số trang (bắt đầu từ 1)
  - `size`: Số lượng mục trên mỗi trang
  - `sort`: Tiêu chí sắp xếp (ví dụ: `createdAt,desc`)
  - `paginationType`: Loại phân trang (`page` hoặc `cursor`)

- Response: (HTTP 200 OK)

```json
{
  "status": "success",
  "code": "USER_LIST_FETCHED",
  "message": "User list retrieved successfully",
  "data": [
    {
      "id": 1,
      "username": "admin",
      "email": "admin@company.com",
      "role": "ADMIN",
      "createdAt": "2025-03-03T08:00:00Z"
    },
    {
      "id": 2,
      "username": "user1",
      "email": "user1@company.com",
      "role": "USER",
      "createdAt": "2025-03-03T09:00:00Z"
    }
  ],
  "page": {
    "totalItems": 100,
    "totalPages": 10,
    "currentPage": 1,
    "pageSize": 10
  }
}
```

#### 2.2 Cập Nhật Quyền của User

- Endpoint: PUT /api/users/1/roles
- Request Body:

```json
{
  "roles": ["MANAGER", "EDITOR"]
}
```

- Response: (HTTP 200 OK hoặc 403 Forbidden)

```json
{
  "status": "success",
  "code": "USER_ROLE_UPDATED",
  "message": "User roles updated successfully"
}
```

#### 2.3 Lấy Thông Tin Chi Tiết Người Dùng

- Endpoint: GET /api/users/1
- Response: (HTTP 200 OK)

```json
{
  "status": "success",
  "code": "USER_DETAIL_FETCHED",
  "message": "User detail retrieved successfully",
  "data": {
    "id": 1,
    "username": "admin",
    "email": "admin@company.com",
    "role": "ADMIN",
    "createdAt": "2025-03-03T08:00:00Z"
  }
}
```

### 3. Policy Service (ABAC/PBAC) APIs

#### 3.1 Tạo Policy Mới

- Endpoint: POST /api/policies
- Request Body:

```json
{
  "resource": "orders",
  "action": "approve",
  "conditions": {
    "role": "MANAGER",
    "department": "Finance"
  }
}
```

- Response: (HTTP 201 Created)

```json
{
  "status": "success",
  "code": "POLICY_CREATED",
  "message": "Policy created successfully"
}
```

### 4. Order Management APIs

#### 4.1 Tạo Đơn Hàng

- Endpoint: POST /api/orders
- Request Body:

```json
{
  "customerId": 123,
  "items": [
    {
      "productId": 1,
      "quantity": 2
    }
  ],
  "orderTime": "2025-03-03T10:00:00Z",
  "timezone": "Asia/Ho_Chi_Minh"
}
```

- Response: (HTTP 201 Created)

```json
{
  "status": "success",
  "code": "ORDER_CREATED",
  "message": "Order placed successfully",
  "data": {
    "orderId": 1001,
    "createdAt": "2025-03-03T03:00:00Z",
    "timezone": "UTC"
  }
}
```

#### 4.2 Lấy Danh Sách Đơn Hàng

- Endpoint: GET /api/orders?page=1&size=10&sort=orderTime,desc&paginationType=page
- Request Parameters:
   - `page`: Số trang (bắt đầu từ 1)
   - `size`: Số lượng mục trên mỗi trang
   - `sort`: Tiêu chí sắp xếp (ví dụ: `orderTime,desc`)
   - `paginationType`: Loại phân trang (`page` hoặc `cursor`)

- Response: (HTTP 200 OK)

```json
{
  "status": "success",
  "code": "ORDER_LIST_FETCHED",
  "message": "Order list retrieved successfully",
  "data": [
    {
      "orderId": 1001,
      "customerId": 123,
      "orderTime": "2025-03-03T10:00:00Z",
      "status": "PENDING"
    },
    {
      "orderId": 1002,
      "customerId": 124,
      "orderTime": "2025-03-03T09:50:00Z",
      "status": "APPROVED"
    }
  ],
  "page": {
    "totalItems": 500,
    "totalPages": 50,
    "currentPage": 1,
    "pageSize": 10
  }
}
```

### 5. Notification Service APIs

#### 5.1 Gửi Thông Báo

- Endpoint: POST /api/notifications
- Request Body:

```json
{
  "userId": 123,
  "message": {
    "en": "Your order has been shipped",
    "vi": "Đơn hàng của bạn đã được gửi đi"
  },
  "type": "EMAIL"
}
```

- Response: (HTTP 200 OK)

```json
{
  "status": "success",
  "code": "NOTIFICATION_SENT",
  "message": "Notification sent successfully"
}
```

### 6. Timezone Service API

#### 6.1 Chuyển Đổi Múi Giờ

- Endpoint: GET /api/timezone/convert?from=Asia/Ho_Chi_Minh&to=America/New_York&time=2025-03-03T10:00:00Z
- Request Parameters:
   - `from`: Múi giờ gốc
   - `to`: Múi giờ đích
   - `time`: Thời gian cần chuyển đổi

- Response: (HTTP 200 OK)

```json
{
  "status": "success",
  "code": "TIMEZONE_CONVERTED",
  "message": "Timezone converted successfully",
  "data": {
    "originalTime": "2025-03-03T10:00:00Z",
    "convertedTime": "2025-03-02T21:00:00Z",
    "from": "Asia/Ho_Chi_Minh",
    "to": "America/New_York"
  }
}
```
```
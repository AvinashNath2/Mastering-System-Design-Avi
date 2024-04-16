
# Coupon Redeeming Service

High-level design for a coupon redeeming microservice along with its Logical Level Design (LLD)

## Requirement:
It should provide functionalities for creating, validating, and redeeming coupons.

#### Functionalities: 
*Generate Coupon*: Generates a new coupon with a unique code and specified attributes. 

*Validate Coupon*: Checks if a coupon is valid (not expired, not redeemed, etc.). 

*Redeem Coupon*: Marks a coupon as redeemed if it's valid and has not exceeded the usage limit. 


## API Reference

REST endpoints for interacting with coupons. 

| Method | Endpoint               | Description                  |
| ------ | ---------------------- | ---------------------------- |
|**POST** | `/api/coupons/generate`           | Generate Coupons |
|**GET** | `/api/coupons/{couponId}/validate` | Validate Coupons |
|**POST** | `/api/coupons/{couponId}/redeem` | Redeem Coupons |
|**GET** | `/api/coupons/{couponId}/redemptionHistory` | Redemption history |


| Path Variable | Type     | Description                |
| :-------- | :------- | :------------------------- |
| `couponId` | `string` | **Required**. Coupon Id |

#### Optionally ####
We can keep track of coupon redemption history for analytics purposes. 
Attributes: Coupon ID User ID Redemption Date Order ID (if the coupon was used in an order) 


## DB Schema's:


|  **Coupons Table**    | | |
| -------- |------ |------- |
| ID  | (Primary Key, Auto-increment) |(Unique identifier for the coupon)     |
| Code |(Unique) | (Alphanumeric code for the coupon)     |
| Discount    | | (Percentage or fixed amount discount)    |
| Coupon Type | | (Coupon Type Percentage or Amount)
| Expiry Date | | (Boolean flag) |
| Usage Limit | | (Maximum number of times the coupon can be redeemed) |
| Status      |  ACTIVE , INPROGRESS , INACTIVE  | is coupon active |




#### Issues In the Service ####
Handling the scenario where only one coupon is left for redemption and multiple user requests for redemption simultaneously is crucial to ensure data consistency and prevent race conditions

**Atomic Check and Update:** 
Implement a mechanism that ensures atomicity when checking and updating the coupon's redemption status. This can be achieved through transactions in relational databases or using atomic operations in NoSQL databases.

**Concurrency Control:** 
Utilize concurrency control mechanisms to handle concurrent requests. For example, you can use locks or optimistic concurrency control techniques to prevent multiple users from redeeming the same coupon simultaneously.

**Queueing or Rate Limiting:** 
Implement a queueing system or rate-limiting mechanism to process redemption requests in a controlled manner. This ensures that only one request is processed at a time when there's only one coupon left.

**Reservation Design pattern:**
Reservation pattern allows you to have a time-bound limit for a coupon so if one coupon have just 1 time use left and one user is redeeming that and if other user comes than that coupon will be unavaible for certain period of time. 

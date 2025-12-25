# 🔥 Advanced Java Multithreading & Concurrency Interview Questions

> **For Senior Java Backend Developers (10+ years experience)**  
> **Working in Highly Concurrent Spring Boot Microservices Environments**

---

## 📋 Table of Contents

- [Concurrency & Race Conditions](#concurrency--race-conditions)
  - [Question 1: Handling concurrent balance updates in a Wallet microservice across multiple instances](#question-1-handling-concurrent-balance-updates-in-a-wallet-microservice-across-multiple-instances)
  - [Question 2: Understanding why synchronized blocks fail under load in distributed systems](#question-2-understanding-why-synchronized-blocks-fail-under-load-in-distributed-systems)
  - [Question 3: Designing idempotent request processing under concurrent execution](#question-3-designing-idempotent-request-processing-under-concurrent-execution)
  - [Question 4: Identifying visibility issues in shared in-memory caches](#question-4-identifying-visibility-issues-in-shared-in-memory-caches)
  - [Question 5: Comparing race conditions in single-instance vs multi-instance Spring Boot deployments](#question-5-comparing-race-conditions-in-single-instance-vs-multi-instance-spring-boot-deployments)

---

## Concurrency & Race Conditions

---

### Question 1: Handling concurrent balance updates in a Wallet microservice across multiple instances

<span style="color: #d32f2f; font-weight: bold; font-size: 1.1em;">In a Wallet microservice where multiple concurrent bets update the same balance, how would you prevent race conditions across multiple instances?</span>

#### Explanation

<span style="color: #333333;">In a distributed microservices architecture, race conditions become significantly more complex than in single-instance applications. When multiple instances of a Wallet service process concurrent balance updates, traditional in-memory synchronization mechanisms like `synchronized` blocks or `ReentrantLock` are ineffective because they only work within a single JVM process. Each microservice instance has its own memory space, so locks cannot coordinate across instances.</span>

<span style="color: #333333;">The core challenge involves ensuring atomicity and consistency when multiple requests attempt to modify the same wallet balance simultaneously across different service instances. Without proper coordination, you risk lost updates, negative balances, or inconsistent state.</span>

<span style="color: #333333;">Common approaches include database-level locking (optimistic or pessimistic), distributed locking using Redis or ZooKeeper, event sourcing with proper ordering, or using database transactions with appropriate isolation levels. The choice depends on your consistency requirements, latency tolerance, and system architecture.</span>

#### Solution Approaches

<span style="color: #555555;">**Approach 1: Database Pessimistic Locking**</span>

<span style="color: #333333;">Use `SELECT FOR UPDATE` to acquire an exclusive lock on the wallet row before updating. This ensures only one transaction can modify the balance at a time, even across multiple instances. Trade-off: Higher latency due to blocking, but guarantees strong consistency.</span>

<span style="color: #555555;">**Approach 2: Optimistic Locking with Versioning**</span>

<span style="color: #333333;">Add a version column to the wallet table. Each update increments the version and checks it hasn't changed. If a concurrent update occurred, the transaction fails and can be retried. Trade-off: Better performance under low contention, but requires retry logic.</span>

<span style="color: #555555;">**Approach 3: Distributed Locking with Redis**</span>

<span style="color: #333333;">Use Redis distributed locks (Redisson or Lettuce) to coordinate updates across instances. Acquire a lock before updating, ensuring only one instance processes updates for a specific wallet at a time. Trade-off: Introduces external dependency and potential lock expiration issues.</span>

<span style="color: #555555;">**Approach 4: Event Sourcing with Single Writer**</span>

<span style="color: #333333;">Use event sourcing where balance updates are events appended to an event log. A single writer pattern ensures events are processed sequentially. Trade-off: More complex architecture but provides audit trail and eventual consistency.</span>

#### Spring Boot Implementation Example

```java
package com.example.wallet.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.dao.OptimisticLockingFailureException;

import com.example.wallet.model.Wallet;
import com.example.wallet.repository.WalletRepository;
import com.example.wallet.exception.InsufficientBalanceException;

import java.math.BigDecimal;

@Service
public class WalletService {
    
    @Autowired
    private WalletRepository walletRepository;
    
    /**
     * Updates wallet balance using optimistic locking with retry mechanism.
     * This approach works across multiple microservice instances.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Retryable(
        value = {OptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public void updateBalance(Long walletId, BigDecimal amount, String transactionId) {
        // Load wallet with current version
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new RuntimeException("Wallet not found: " + walletId));
        
        // Check for duplicate transaction (idempotency)
        if (wallet.getProcessedTransactions().contains(transactionId)) {
            return; // Already processed
        }
        
        // Validate balance for debit operations
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal newBalance = wallet.getBalance().add(amount);
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new InsufficientBalanceException(
                    "Insufficient balance. Current: " + wallet.getBalance()
                );
            }
        }
        
        // Update balance and version atomically
        wallet.setBalance(wallet.getBalance().add(amount));
        wallet.addProcessedTransaction(transactionId);
        
        // Save will fail if version changed (optimistic lock)
        walletRepository.save(wallet);
    }
    
    /**
     * Alternative: Pessimistic locking approach for high-contention scenarios
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void updateBalancePessimistic(Long walletId, BigDecimal amount, String transactionId) {
        // Lock the row exclusively until transaction completes
        Wallet wallet = walletRepository.findByIdWithLock(walletId)
            .orElseThrow(() -> new RuntimeException("Wallet not found: " + walletId));
        
        if (wallet.getProcessedTransactions().contains(transactionId)) {
            return;
        }
        
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal newBalance = wallet.getBalance().add(amount);
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new InsufficientBalanceException("Insufficient balance");
            }
        }
        
        wallet.setBalance(wallet.getBalance().add(amount));
        wallet.addProcessedTransaction(transactionId);
        walletRepository.save(wallet);
    }
}
```

```java
package com.example.wallet.repository;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import com.example.wallet.model.Wallet;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    
    /**
     * Optimistic locking is handled automatically via @Version annotation
     */
    Optional<Wallet> findById(Long id);
    
    /**
     * Pessimistic locking: SELECT FOR UPDATE
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdWithLock(@Param("id") Long id);
}
```

```java
package com.example.wallet.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "wallets", indexes = @Index(name = "idx_user_id", columnList = "userId"))
public class Wallet {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long userId;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;
    
    /**
     * Version field for optimistic locking
     */
    @Version
    private Long version;
    
    /**
     * Track processed transactions for idempotency
     */
    @ElementCollection
    @CollectionTable(name = "wallet_transactions", joinColumns = @JoinColumn(name = "wallet_id"))
    @Column(name = "transaction_id")
    private Set<String> processedTransactions = new HashSet<>();
    
    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    
    public Set<String> getProcessedTransactions() { return processedTransactions; }
    public void addProcessedTransaction(String transactionId) {
        this.processedTransactions.add(transactionId);
    }
}
```

---

### Question 2: Understanding why synchronized blocks fail under load in distributed systems

<span style="color: #d32f2f; font-weight: bold; font-size: 1.1em;">A Payment service uses synchronized blocks but still produces inconsistent results under load. Why?</span>

#### Explanation

<span style="color: #333333;">The fundamental issue with `synchronized` blocks in distributed microservices is that they only provide thread-level synchronization within a single JVM process. When your application scales horizontally across multiple instances (pods, containers, or servers), each instance runs in its own JVM with its own memory space. A `synchronized` block in Instance A cannot prevent a thread in Instance B from accessing the same resource simultaneously.</span>

<span style="color: #333333;">Additionally, even within a single instance, `synchronized` blocks can fail under high load due to several reasons: lock contention causing thread starvation, improper lock scope leading to race conditions, or synchronization on incorrect objects. However, the most critical failure mode in microservices is the distributed nature of the system.</span>

<span style="color: #333333;">Consider a scenario where two payment requests for the same account arrive simultaneously at different service instances. Both instances might read the current balance, perform calculations, and write back results, leading to lost updates or incorrect final balances. The `synchronized` block in each instance only coordinates threads within that instance, not across instances.</span>

#### Solution Approaches

<span style="color: #555555;">**Approach 1: Move Synchronization to Database Layer**</span>

<span style="color: #333333;">Use database transactions with appropriate isolation levels (SERIALIZABLE or REPEATABLE_READ) and pessimistic locking. The database becomes the single source of truth and coordinates access across all instances. This is the most common and reliable approach for financial systems.</span>

<span style="color: #555555;">**Approach 2: Distributed Locking with Redis/ZooKeeper**</span>

<span style="color: #333333;">Implement distributed locks using Redis (Redisson) or ZooKeeper. Before processing a payment, acquire a distributed lock for the account ID. This ensures only one instance processes payments for that account at a time. Must handle lock expiration and deadlock scenarios carefully.</span>

<span style="color: #555555;">**Approach 3: Single Writer Pattern with Message Queue**</span>

<span style="color: #333333;">Route all payment requests for a specific account through a message queue with partitioning. Use account ID as the partition key to ensure all requests for the same account are processed sequentially by a single consumer instance. This provides natural serialization without explicit locking.</span>

<span style="color: #555555;">**Approach 4: Event Sourcing with CQRS**</span>

<span style="color: #333333;">Store payment events in an event log. Use a single writer pattern for events and rebuild state from events. This eliminates the need for distributed locking but requires more complex architecture and eventual consistency handling.</span>

#### Spring Boot Implementation Example

```java
package com.example.payment.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.payment.model.Payment;
import com.example.payment.model.Account;
import com.example.payment.repository.AccountRepository;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.exception.PaymentProcessingException;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentService {
    
    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private PaymentRepository paymentRepository;
    
    @Autowired(required = false)
    private RedissonClient redissonClient; // Optional: for distributed locking
    
    /**
     * Approach 1: Database-level synchronization (Recommended)
     * This works across multiple instances because the database coordinates access
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Payment processPayment(Long accountId, BigDecimal amount, String paymentId) {
        // Pessimistic lock: SELECT FOR UPDATE ensures exclusive access
        Account account = accountRepository.findByIdWithLock(accountId)
            .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));
        
        // Check for duplicate payment (idempotency)
        if (paymentRepository.existsByPaymentId(paymentId)) {
            logger.info("Payment already processed: {}", paymentId);
            return paymentRepository.findByPaymentId(paymentId);
        }
        
        // Validate and update balance atomically
        if (account.getBalance().compareTo(amount) < 0) {
            throw new PaymentProcessingException("Insufficient balance");
        }
        
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        
        // Create payment record
        Payment payment = new Payment();
        payment.setAccountId(accountId);
        payment.setAmount(amount);
        payment.setPaymentId(paymentId);
        payment.setStatus("COMPLETED");
        
        return paymentRepository.save(payment);
    }
    
    /**
     * Approach 2: Distributed locking with Redis (Alternative)
     * Use when you need coordination beyond database transactions
     */
    @Transactional
    public Payment processPaymentWithDistributedLock(Long accountId, BigDecimal amount, String paymentId) {
        if (redissonClient == null) {
            throw new IllegalStateException("RedissonClient not configured");
        }
        
        String lockKey = "payment:lock:" + accountId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // Try to acquire lock with timeout
            boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!acquired) {
                throw new PaymentProcessingException("Could not acquire lock for account: " + accountId);
            }
            
            try {
                // Now process payment (still use transaction for consistency)
                Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));
                
                if (paymentRepository.existsByPaymentId(paymentId)) {
                    return paymentRepository.findByPaymentId(paymentId);
                }
                
                if (account.getBalance().compareTo(amount) < 0) {
                    throw new PaymentProcessingException("Insufficient balance");
                }
                
                account.setBalance(account.getBalance().subtract(amount));
                accountRepository.save(account);
                
                Payment payment = new Payment();
                payment.setAccountId(accountId);
                payment.setAmount(amount);
                payment.setPaymentId(paymentId);
                payment.setStatus("COMPLETED");
                
                return paymentRepository.save(payment);
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentProcessingException("Lock acquisition interrupted", e);
        }
    }
    
    /**
     * WRONG APPROACH: This fails in distributed systems
     * synchronized only works within a single JVM instance
     */
    private final Object lockObject = new Object();
    
    @Deprecated
    public Payment processPaymentWrong(Long accountId, BigDecimal amount, String paymentId) {
        synchronized (lockObject) { // ❌ Only works within this instance!
            // This will fail when multiple instances process payments
            Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));
            
            if (account.getBalance().compareTo(amount) < 0) {
                throw new PaymentProcessingException("Insufficient balance");
            }
            
            account.setBalance(account.getBalance().subtract(amount));
            accountRepository.save(account);
            
            Payment payment = new Payment();
            payment.setAccountId(accountId);
            payment.setAmount(amount);
            payment.setPaymentId(paymentId);
            payment.setStatus("COMPLETED");
            
            return paymentRepository.save(payment);
        }
    }
}
```

```java
package com.example.payment.repository;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import com.example.payment.model.Account;
import com.example.payment.model.Payment;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    
    /**
     * Pessimistic write lock: SELECT FOR UPDATE
     * This ensures exclusive access across all instances
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") Long id);
}

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByPaymentId(String paymentId);
    Payment findByPaymentId(String paymentId);
}
```

```java
// application.yml configuration for distributed locking
spring:
  redis:
    host: localhost
    port: 6379
    timeout: 2000ms

# Redisson configuration (if using Redisson)
redisson:
  single-server-config:
    address: "redis://localhost:6379"
    connection-pool-size: 10
```

---

### Question 3: Designing idempotent request processing under concurrent execution

<span style="color: #d32f2f; font-weight: bold; font-size: 1.1em;">How would you guarantee idempotency when the same request is processed concurrently by multiple threads?</span>

#### Explanation

<span style="color: #333333;">Idempotency ensures that processing the same request multiple times produces the same result as processing it once. Under concurrent execution, the challenge is preventing duplicate processing when the same request arrives simultaneously at different threads or instances. Without proper idempotency mechanisms, you risk double-charging, duplicate orders, or inconsistent state.</span>

<span style="color: #333333;">The key to idempotency in concurrent systems is using a unique request identifier (idempotency key) and ensuring atomic check-and-set operations. The critical requirement is that checking if a request was processed and marking it as processed must be atomic. If these operations are not atomic, two threads might both see the request as unprocessed and both execute it.</span>

<span style="color: #333333;">Common patterns include database unique constraints on idempotency keys, Redis SETNX operations, or database transactions with proper isolation levels. The idempotency key should be provided by the client or generated deterministically from request parameters.</span>

#### Solution Approaches

<span style="color: #555555;">**Approach 1: Database Unique Constraint with Upsert**</span>

<span style="color: #333333;">Store idempotency keys in a database table with a unique constraint. Use database upsert operations (INSERT ... ON CONFLICT or MERGE) to atomically check and insert. If the key exists, return the previous result. This works across all instances and provides strong consistency.</span>

<span style="color: #555555;">**Approach 2: Redis SETNX with Expiration**</span>

<span style="color: #333333;">Use Redis SETNX (SET if Not eXists) to atomically set an idempotency key. If SETNX succeeds, process the request and store the result. If it fails, the key exists, so return the cached result. Set expiration to prevent indefinite key accumulation. Fast but requires Redis availability.</span>

<span style="color: #555555;">**Approach 3: Database Transaction with SELECT FOR UPDATE**</span>

<span style="color: #333333;">Within a transaction, use SELECT FOR UPDATE to lock the idempotency record. Check if processed, and if not, process and mark as processed. This ensures only one thread processes the request, even under high concurrency. More database load but very reliable.</span>

<span style="color: #555555;">**Approach 4: Idempotency Service with Distributed Lock**</span>

<span style="color: #333333;">Create a dedicated idempotency service that uses distributed locking to coordinate across instances. The service maintains idempotency state and results. All instances check with this service before processing. Centralized but adds network latency.</span>

#### Spring Boot Implementation Example

```java
package com.example.order.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.order.model.Order;
import com.example.order.model.IdempotencyRecord;
import com.example.order.repository.OrderRepository;
import com.example.order.repository.IdempotencyRepository;
import com.example.order.dto.OrderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

@Service
public class OrderService {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private IdempotencyRepository idempotencyRepository;
    
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Approach 1: Database-based idempotency with unique constraint
     * Most reliable and works across all instances
     */
    @Transactional
    public Order createOrder(OrderRequest request, String idempotencyKey) {
        // Try to find existing idempotency record
        IdempotencyRecord existingRecord = idempotencyRepository
            .findByIdempotencyKey(idempotencyKey);
        
        if (existingRecord != null) {
            logger.info("Idempotent request detected: {}", idempotencyKey);
            // Return the previously created order
            return orderRepository.findById(existingRecord.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));
        }
        
        // Create order
        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setItems(request.getItems());
        order.setTotalAmount(request.getTotalAmount());
        order.setStatus("CREATED");
        
        order = orderRepository.save(order);
        
        // Create idempotency record atomically
        // If concurrent request arrives, unique constraint will prevent duplicate
        try {
            IdempotencyRecord record = new IdempotencyRecord();
            record.setIdempotencyKey(idempotencyKey);
            record.setOrderId(order.getId());
            record.setRequestHash(calculateRequestHash(request));
            idempotencyRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            // Another thread/instance processed this request concurrently
            logger.warn("Concurrent idempotency key detected: {}", idempotencyKey);
            // Rollback order creation and return existing order
            orderRepository.delete(order);
            throw new RuntimeException("Concurrent request detected", e);
        }
        
        return order;
    }
    
    /**
     * Approach 2: Redis-based idempotency (faster, but requires Redis)
     */
    @Transactional
    public Order createOrderWithRedis(OrderRequest request, String idempotencyKey) {
        if (redisTemplate == null) {
            throw new IllegalStateException("Redis not configured");
        }
        
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        String redisKey = "idempotency:" + idempotencyKey;
        
        // Try to set key atomically (SETNX)
        Boolean setIfAbsent = ops.setIfAbsent(redisKey, "processing", 60, TimeUnit.SECONDS);
        
        if (!setIfAbsent) {
            // Key exists - this is a duplicate request
            logger.info("Idempotent request detected via Redis: {}", idempotencyKey);
            String cachedOrderId = ops.get(redisKey);
            if (cachedOrderId != null && !cachedOrderId.equals("processing")) {
                return orderRepository.findById(Long.parseLong(cachedOrderId))
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            }
            // Still processing, wait or return error
            throw new RuntimeException("Request is being processed");
        }
        
        try {
            // Process order
            Order order = new Order();
            order.setUserId(request.getUserId());
            order.setItems(request.getItems());
            order.setTotalAmount(request.getTotalAmount());
            order.setStatus("CREATED");
            order = orderRepository.save(order);
            
            // Update Redis with order ID
            ops.set(redisKey, String.valueOf(order.getId()), 60, TimeUnit.SECONDS);
            
            return order;
        } catch (Exception e) {
            // Remove key on failure to allow retry
            redisTemplate.delete(redisKey);
            throw e;
        }
    }
    
    /**
     * Approach 3: Database transaction with SELECT FOR UPDATE
     * Ensures only one thread processes even under extreme concurrency
     */
    @Transactional
    public Order createOrderWithLock(OrderRequest request, String idempotencyKey) {
        // Lock the idempotency record exclusively
        IdempotencyRecord record = idempotencyRepository
            .findByIdempotencyKeyWithLock(idempotencyKey);
        
        if (record != null) {
            // Already processed
            return orderRepository.findById(record.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));
        }
        
        // Create new record to lock (prevents concurrent processing)
        record = new IdempotencyRecord();
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestHash(calculateRequestHash(request));
        record = idempotencyRepository.save(record);
        
        // Now locked, create order
        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setItems(request.getItems());
        order.setTotalAmount(request.getTotalAmount());
        order.setStatus("CREATED");
        order = orderRepository.save(order);
        
        // Update record with order ID
        record.setOrderId(order.getId());
        idempotencyRepository.save(record);
        
        return order;
    }
    
    private String calculateRequestHash(OrderRequest request) {
        try {
            return String.valueOf(objectMapper.writeValueAsString(request).hashCode());
        } catch (Exception e) {
            return String.valueOf(request.hashCode());
        }
    }
}
```

```java
package com.example.order.repository;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import com.example.order.model.IdempotencyRecord;

import jakarta.persistence.LockModeType;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, Long> {
    
    IdempotencyRecord findByIdempotencyKey(String idempotencyKey);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM IdempotencyRecord i WHERE i.idempotencyKey = :key")
    IdempotencyRecord findByIdempotencyKeyWithLock(@Param("key") String idempotencyKey);
}
```

```java
package com.example.order.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_records", 
       uniqueConstraints = @UniqueConstraint(columnNames = "idempotencyKey"))
public class IdempotencyRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false, length = 255)
    private String idempotencyKey;
    
    private Long orderId;
    
    private String requestHash;
    
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

---

### Question 4: Identifying visibility issues in shared in-memory caches

<span style="color: #d32f2f; font-weight: bold; font-size: 1.1em;">What visibility issues can occur if a shared in-memory cache is updated by multiple threads?</span>

#### Explanation

<span style="color: #333333;">Visibility issues in Java occur when changes made by one thread to shared variables are not immediately visible to other threads. This happens due to CPU caching, compiler optimizations, and the Java Memory Model (JMM). Each thread may have its own cached copy of variables in CPU registers or local cache, and without proper synchronization, updates might not propagate to other threads.</span>

<span style="color: #333333;">In a shared in-memory cache scenario, multiple threads reading and writing cache entries can experience stale reads, lost updates, or inconsistent state. For example, Thread A might update a cache entry, but Thread B continues to see the old value because it's reading from its cached copy. This is particularly problematic in high-concurrency scenarios where cache performance is critical.</span>

<span style="color: #333333;">Common visibility issues include: reading stale values after updates, seeing partially updated objects (non-atomic updates), and cache coherency problems where different threads see different versions of the same data. Solutions involve using volatile variables, synchronized blocks, concurrent collections, or thread-safe cache implementations.</span>

#### Solution Approaches

<span style="color: #555555;">**Approach 1: Use ConcurrentHashMap**</span>

<span style="color: #333333;">Replace HashMap with ConcurrentHashMap for thread-safe operations. ConcurrentHashMap uses fine-grained locking and provides visibility guarantees. All operations are thread-safe and updates are immediately visible to other threads. Best for high-read scenarios with moderate writes.</span>

<span style="color: #555555;">**Approach 2: Use Caffeine or Guava Cache with Thread Safety**</span>

<span style="color: #333333;">Use production-ready cache libraries like Caffeine or Guava Cache that are designed for concurrent access. These provide built-in thread safety, eviction policies, and performance optimizations. They handle visibility internally using proper synchronization.</span>

<span style="color: #555555;">**Approach 3: Synchronized Wrapper with ReadWriteLock**</span>

<span style="color: #333333;">Wrap cache operations with ReadWriteLock to allow multiple concurrent readers but exclusive writers. This improves performance for read-heavy workloads while ensuring write visibility. More complex to implement but provides fine-grained control.</span>

<span style="color: #555555;">**Approach 4: Volatile with Atomic Operations**</span>

<span style="color: #333333;">For simple cache scenarios, use volatile references combined with atomic operations. Volatile ensures visibility (but not atomicity), so combine with AtomicReference or similar for compound operations. Limited to simple use cases.</span>

#### Spring Boot Implementation Example

```java
package com.example.cache.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.example.product.model.Product;
import com.example.product.repository.ProductRepository;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class ProductCacheService {
    
    @Autowired
    private ProductRepository productRepository;
    
    // Approach 1: ConcurrentHashMap (Thread-safe by design)
    private final ConcurrentHashMap<Long, Product> productCache = new ConcurrentHashMap<>();
    
    // Approach 2: ReadWriteLock for fine-grained control
    private final ConcurrentHashMap<Long, Product> lockedCache = new ConcurrentHashMap<>();
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    
    /**
     * Approach 1: Using ConcurrentHashMap
     * All operations are thread-safe and provide visibility guarantees
     */
    public Product getProductConcurrent(Long productId) {
        // Check cache first (thread-safe read)
        Product product = productCache.get(productId);
        
        if (product != null) {
            return product; // Return cached copy (immutable or defensive copy)
        }
        
        // Cache miss - load from database
        product = productRepository.findById(productId)
            .orElseThrow(() -> new RuntimeException("Product not found"));
        
        // Put in cache (thread-safe write, visible to all threads immediately)
        productCache.put(productId, product);
        
        return product;
    }
    
    /**
     * Update cache entry (ConcurrentHashMap handles visibility)
     */
    public void updateProductCache(Long productId, Product updatedProduct) {
        // ConcurrentHashMap.put() is atomic and provides visibility
        productCache.put(productId, updatedProduct);
        // All threads will see this update immediately
    }
    
    /**
     * Approach 2: Using ReadWriteLock for read-heavy scenarios
     * Allows multiple concurrent readers, exclusive writers
     */
    public Product getProductWithLock(Long productId) {
        cacheLock.readLock().lock();
        try {
            Product product = lockedCache.get(productId);
            if (product != null) {
                return product;
            }
        } finally {
            cacheLock.readLock().unlock();
        }
        
        // Upgrade to write lock for cache update
        cacheLock.writeLock().lock();
        try {
            // Double-check after acquiring write lock
            Product product = lockedCache.get(productId);
            if (product != null) {
                return product;
            }
            
            // Load from database
            product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
            
            // Update cache (visible to all threads after unlock)
            lockedCache.put(productId, product);
            
            return product;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Approach 3: Using Spring Cache with Caffeine (Recommended for production)
     * Spring handles thread safety and visibility automatically
     */
    @Cacheable(value = "products", key = "#productId")
    public Product getProductCached(Long productId) {
        // This method is only called on cache miss
        // Spring Cache handles all thread safety and visibility
        return productRepository.findById(productId)
            .orElseThrow(() -> new RuntimeException("Product not found"));
    }
    
    @Caching(evict = {
        @CacheEvict(value = "products", key = "#product.id")
    })
    public Product updateProduct(Product product) {
        Product updated = productRepository.save(product);
        // Cache is automatically evicted, next read will refresh
        return updated;
    }
    
    /**
     * WRONG APPROACH: Non-thread-safe HashMap
     * This will cause visibility issues and potential data corruption
     */
    @Deprecated
    private final java.util.HashMap<Long, Product> unsafeCache = new java.util.HashMap<>();
    
    @Deprecated
    public Product getProductUnsafe(Long productId) {
        // ❌ No synchronization - visibility issues!
        Product product = unsafeCache.get(productId); // May see stale value
        
        if (product == null) {
            product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
            // ❌ Concurrent modification exception possible
            unsafeCache.put(productId, product); // Not visible to other threads immediately
        }
        
        return product; // May return stale or partially updated object
    }
}
```

```java
package com.example.cache.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {
    
    /**
     * Caffeine cache configuration for thread-safe, high-performance caching
     * Provides automatic visibility guarantees and thread safety
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("products");
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }
    
    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
            .maximumSize(10_000) // Maximum entries
            .expireAfterWrite(30, TimeUnit.MINUTES) // TTL
            .recordStats(); // Enable statistics
    }
}
```

```java
// application.yml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=10000,expireAfterWrite=30m
```

---

### Question 5: Comparing race conditions in single-instance vs multi-instance Spring Boot deployments

<span style="color: #d32f2f; font-weight: bold; font-size: 1.1em;">How do race conditions differ between single-instance and multi-instance Spring Boot deployments?</span>

#### Explanation

<span style="color: #333333;">Race conditions manifest differently depending on whether your Spring Boot application runs as a single instance or multiple instances. In a single-instance deployment, race conditions occur between threads within the same JVM, and traditional Java synchronization mechanisms (synchronized, ReentrantLock, volatile) can effectively prevent them. However, in multi-instance deployments, each instance runs in its own JVM with separate memory spaces, making in-memory synchronization ineffective.</span>

<span style="color: #333333;">In single-instance scenarios, race conditions typically involve shared in-memory state accessed by multiple threads. For example, two threads updating a shared counter or modifying a shared collection. These can be prevented with proper synchronization within the JVM. The challenge is ensuring all shared state is properly protected.</span>

<span style="color: #333333;">In multi-instance deployments, race conditions occur when multiple instances process requests that modify shared external state (database, cache, message queue). Each instance might read the same state, modify it independently, and write back, leading to lost updates. In-memory locks are useless here because they don't coordinate across JVMs. You need distributed coordination mechanisms.</span>

#### Solution Approaches

<span style="color: #555555;">**Approach 1: Database-Level Coordination**</span>

<span style="color: #333333;">Use database transactions with appropriate isolation levels and locking (pessimistic or optimistic). The database becomes the coordination point across all instances. Works for both single and multi-instance, but is essential for multi-instance.</span>

<span style="color: #555555;">**Approach 2: Distributed Locking**</span>

<span style="color: #333333;">Use Redis, ZooKeeper, or database-based distributed locks to coordinate across instances. Before modifying shared state, acquire a distributed lock. This ensures only one instance processes at a time, regardless of instance count.</span>

<span style="color: #555555;">**Approach 3: Single Writer Pattern**</span>

<span style="color: #333333;">Route all updates for a specific resource through a single instance using message queue partitioning or consistent hashing. This eliminates the need for distributed locking but requires careful routing logic.</span>

<span style="color: #555555;">**Approach 4: Event Sourcing**</span>

<span style="color: #333333;">Store all changes as events in an event log. Use a single writer or proper partitioning to ensure sequential processing. Rebuild state from events. Eliminates race conditions but requires more complex architecture.</span>

#### Spring Boot Implementation Example

```java
package com.example.inventory.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.inventory.model.Inventory;
import com.example.inventory.repository.InventoryRepository;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class InventoryService {
    
    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);
    
    @Autowired
    private InventoryRepository inventoryRepository;
    
    @Autowired(required = false)
    private RedissonClient redissonClient;
    
    @Value("${app.deployment.mode:single}")
    private String deploymentMode;
    
    // Single-instance lock (only works within one JVM)
    private final ReentrantLock inMemoryLock = new ReentrantLock();
    
    /**
     * SINGLE-INSTANCE APPROACH: In-memory synchronization
     * This works ONLY when running a single instance
     * ❌ FAILS in multi-instance deployments
     */
    @Transactional
    public void updateInventorySingleInstance(Long productId, int quantityChange) {
        inMemoryLock.lock(); // Only coordinates threads in THIS JVM
        try {
            Inventory inventory = inventoryRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
            
            int newQuantity = inventory.getQuantity() + quantityChange;
            if (newQuantity < 0) {
                throw new RuntimeException("Insufficient inventory");
            }
            
            inventory.setQuantity(newQuantity);
            inventoryRepository.save(inventory);
        } finally {
            inMemoryLock.unlock();
        }
    }
    
    /**
     * MULTI-INSTANCE APPROACH 1: Database-level locking
     * Works across all instances because database coordinates access
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void updateInventoryMultiInstance(Long productId, int quantityChange) {
        // Pessimistic lock: SELECT FOR UPDATE
        // This works across ALL instances because the database coordinates
        Inventory inventory = inventoryRepository.findByIdWithLock(productId)
            .orElseThrow(() -> new RuntimeException("Product not found"));
        
        int newQuantity = inventory.getQuantity() + quantityChange;
        if (newQuantity < 0) {
            throw new RuntimeException("Insufficient inventory");
        }
        
        inventory.setQuantity(newQuantity);
        inventoryRepository.save(inventory);
        // Lock released when transaction commits
    }
    
    /**
     * MULTI-INSTANCE APPROACH 2: Distributed locking with Redis
     * Coordinates across all instances using external lock service
     */
    @Transactional
    public void updateInventoryDistributedLock(Long productId, int quantityChange) {
        if (redissonClient == null) {
            throw new IllegalStateException("Redis not configured for distributed locking");
        }
        
        String lockKey = "inventory:lock:" + productId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // Try to acquire distributed lock (works across all instances)
            boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!acquired) {
                throw new RuntimeException("Could not acquire lock for product: " + productId);
            }
            
            try {
                // Now safe to update (still use transaction for consistency)
                Inventory inventory = inventoryRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Product not found"));
                
                int newQuantity = inventory.getQuantity() + quantityChange;
                if (newQuantity < 0) {
                    throw new RuntimeException("Insufficient inventory");
                }
                
                inventory.setQuantity(newQuantity);
                inventoryRepository.save(inventory);
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock acquisition interrupted", e);
        }
    }
    
    /**
     * UNIVERSAL APPROACH: Works for both single and multi-instance
     * Uses database coordination which works in all scenarios
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void updateInventoryUniversal(Long productId, int quantityChange) {
        // Always use database-level coordination
        // This works whether you have 1 instance or 100 instances
        Inventory inventory = inventoryRepository.findByIdWithLock(productId)
            .orElseThrow(() -> new RuntimeException("Product not found"));
        
        int newQuantity = inventory.getQuantity() + quantityChange;
        if (newQuantity < 0) {
            throw new RuntimeException("Insufficient inventory");
        }
        
        inventory.setQuantity(newQuantity);
        inventoryRepository.save(inventory);
    }
    
    /**
     * Detects deployment mode and uses appropriate strategy
     */
    public void updateInventoryAdaptive(Long productId, int quantityChange) {
        if ("single".equals(deploymentMode)) {
            // Can use in-memory locks for performance
            updateInventorySingleInstance(productId, quantityChange);
        } else {
            // Must use distributed coordination
            updateInventoryMultiInstance(productId, quantityChange);
        }
    }
}
```

```java
package com.example.inventory.repository;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import com.example.inventory.model.Inventory;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    
    /**
     * Standard find - no locking (unsafe for concurrent updates)
     */
    Optional<Inventory> findById(Long id);
    
    /**
     * Pessimistic write lock: SELECT FOR UPDATE
     * Works across ALL instances because database coordinates access
     * This is the key difference: database locks work in multi-instance deployments
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.id = :id")
    Optional<Inventory> findByIdWithLock(@Param("id") Long id);
}
```

```java
package com.example.inventory.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory")
public class Inventory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long productId;
    
    @Column(nullable = false)
    private Integer quantity;
    
    @Version // For optimistic locking alternative
    private Long version;
    
    private LocalDateTime lastUpdated = LocalDateTime.now();
    
    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
```

```java
// application.yml - Configuration for deployment mode
app:
  deployment:
    mode: ${DEPLOYMENT_MODE:multi} # single or multi

# Redis configuration for distributed locking (multi-instance)
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    timeout: 2000ms
```

---

## Summary

<span style="color: #333333;">These questions cover critical aspects of concurrency and race conditions in Spring Boot microservices. Key takeaways:</span>

- <span style="color: #333333;">**Single-instance vs Multi-instance**: In-memory synchronization only works within one JVM. Multi-instance deployments require database-level or distributed coordination.</span>
- <span style="color: #333333;">**Idempotency**: Essential for handling duplicate requests. Use database unique constraints or Redis SETNX for atomic check-and-set operations.</span>
- <span style="color: #333333;">**Visibility**: Use thread-safe collections (ConcurrentHashMap) or proper synchronization to ensure updates are visible across threads.</span>
- <span style="color: #333333;">**Database Coordination**: Pessimistic locking (SELECT FOR UPDATE) is the most reliable approach for coordinating updates across instances.</span>

---


# TimeDeal Pro (대규모 트래픽 대응 커머스 엔진)

**TimeDeal Pro**는 초당 수천 건의 트래픽이 집중되는 선착순 구매 환경을 가정하여 설계된 고성능 백엔드 시스템입니다. 단순 기능 구현을 넘어 **데이터 일관성 모델, 조회 성능 튜닝, 복잡한 비즈니스 정책의 객체지향적 분리**를 심도 있게 다룹니다.

---

## 📊 벤치마크 결과 요약 (Benchmark Summary)

`PerformanceComparisonTest`를 통해 측정된 실제 수치입니다.

| **Scenario** | **개선 항목** | **Before (Naive)** | **After (Optimized)** | **개선 성능** |
| --- | --- | --- | --- | --- |
| **S1** | **동시성 제어** | 284ms (성공 5회) | **232ms (성공 10회)** | **정합성 100% 확보** |
| **S3&4** | **조회 최적화** | 314ms (쿼리 101회) | **43ms (쿼리 2회)** | **시간 86% 단축** |
| **S2&5** | **설계 구조화** | 1,502ms | **987ms** | **효율 34% 향상** |

---

## 💡 핵심 문제 해결 및 기술적 의사결정

### 1️⃣ 시나리오 1: 초고동시성 재고 정합성 (Concurrency)

- **문제**: 100개 스레드 동시 요청 시 `Lost Update`로 인한 재고 유실 발생.
- **해결**: **비관적 락(`PESSIMISTIC_WRITE`)** 도입.
- **결과**: 정확히 재고 수량(10개)만큼만 주문 성공 및 재고 0개 도달 확인.

### 2️⃣ 시나리오 5: 결제 프로세스 및 원자적 롤백 (Payment Flow)

- **문제**: PG 결제 실패 혹은 금액 변조 시 재고만 차감되는 데이터 정합성 위협.
- **해결**: PG 호출 전 **금액 검증 로직** 및 예외 발생 시 **트랜잭션 자동 롤백**.
- **결과**: `PaymentRollbackIntegrationTest`를 통해 어떤 장애 상황에서도 재고가 100% 원복됨을 증명.

### 3️⃣ 시나리오 3 & 4: N+1 문제와 동적 검색 (Query Tuning)

- **문제**: 주문 목록 조회 시 연관 엔티티(User, Item, Payment)의 지연 로딩으로 인한 쿼리 폭증.
- **해결**: **Fetch Join**과 **QueryDSL** Projections 사용.
- **결과**: 100건 조회 시 101번의 쿼리를 단 1~2회로 압축하여 조회 속도 86% 개선.

---

## 📈 Troubleshooting 기록 (심화 분석)

### 🚨 이슈 1: 비관적 락 도입 후 DB 커넥션 풀 고갈

- **원인**: 수천 명의 사용자가 락을 획득하기 위해 대기하면서 DB 커넥션을 점유한 채 놓아주지 않아 시스템 마비 발생.
- **해결**: **Fail-Fast** 전략을 위한 락 타임아웃 설정.

```java
// ❌ Before: 무한 대기 가능성 (DB 기본값 의존)
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Item> findById(Long id);

// ✅ After: 3초 타임아웃 설정으로 커넥션 점유 최소화
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints({@QueryHint(name = "javax.persistence.lock.timeout", value = "3000")})
Optional<Item> findByIdWithTimeout(Long id);
```

### 🚨 이슈 2: 1:N Fetch Join 시 페이징 처리 경고 및 성능 저하

- **원인**: 컬렉션 Fetch Join과 페이징 병행 시 하이버네이트가 데이터를 메모리에 모두 올려 페이징을 수행(OOM 위험).
- **해결**: To-One 관계는 **Fetch Join**, To-Many 관계는 **Batch Size** 적용.

```java
// ❌ Before: 컬렉션까지 Fetch Join (메모리 페이징 유발)
@Query("select o from Order o join fetch o.user join fetch o.orderItems")
Page<Order> findAllWithItems(Pageable pageable);

// ✅ After: Batch Size 설정으로 1+N을 1+1로 최적화
// application.yml: spring.jpa.properties.hibernate.default_batch_fetch_size: 100
@Query("select o from Order o join fetch o.user") // To-One만 페치조인
Page<Order> findAllOptimized(Pageable pageable);
```

### 🚨 이슈 3: 외부 결제 시스템(PG) 금액 불일치 사고

- **원인**: 클라이언트의 결제 요청 금액과 실제 PG사 승인 금액이 다를 경우(금액 변조 등)에도 주문이 생성되는 결함.
- **해결**: `SwitchableTestPaymentGateway`를 활용한 **금액 교차 검증 로직** 구현.

```java
// ❌ Before: 승인 성공 여부만 확인
if (result.isSuccess()) { payment.markSuccess(); }

// ✅ After: 실제 승인된 금액이 요청 금액과 일치하는지 엄격 검증
if (result.isSuccess()) {
    if (result.getApprovedAmount() != finalAmount) {
        throw new PriceTamperedException("승인 금액 불일치!"); // 롤백 유발
    }
    payment.markSuccess();
}
```

### 🚨 이슈 4: 대량 조회 시 Hibernate Statistics를 통한 병목 식별

- **원인**: 눈에 보이지 않는 N+1 쿼리가 시스템 부하의 주범임을 `Statistics` 지표로 확인.
- **해결**: `Statistics` 모니터링을 통한 쿼리 수 수치화 및 개선.

```java
// ❌ Before: 승인 성공 여부만 확인
if (result.isSuccess()) { payment.markSuccess(); }

// ✅ After: 실제 승인된 금액이 요청 금액과 일치하는지 엄격 검증
if (result.isSuccess()) {
    if (result.getApprovedAmount() != finalAmount) {
        throw new PriceTamperedException("승인 금액 불일치!"); // 롤백 유발
    }
    payment.markSuccess();
}
```

### 🚨 이슈 5: Git 초기화 및 브랜치 푸시 오류

- **현상**: `src refspec main does not match any`
- **원인**: 커밋 내역이 없는 상태에서 푸시 시도.
- **해결**: `git add .` -> `git commit`을 통해 로컬 스냅샷을 생성한 후 `push` 성공. Git 브랜치가 커밋 객체를 가리키는 포인터임을 학습.

---

## 🛠 Tech Stack

- **Persistence**: Spring Data JPA, **QueryDSL 5.0**
- **Concurrency**: DB **Pessimistic Lock** (Row Level)
- **Architecture**: **Strategy Pattern**, Validator Pattern
- **Testing**: **JUnit5**, **Hibernate Statistics** (성능 측정 자동화)

🏗️ 1단계: 프로젝트 기초 공사 (환경 및 엔티티)
목표: 데이터베이스 구조를 잡고 모든 테이블 간의 연관관계를 설정합니다.

build.gradle 작성: "QueryDSL 5.0, JPA, MySQL, Lombok 설정이 포함된 build.gradle을 작성해줘."

공통 엔티티: "생성일과 수정일을 자동으로 기록하는 BaseEntity를 만들어줘."

7개 핵심 엔티티 구현: "명세서에 있는 7개 엔티티(User, Item, Category, Coupon, UserCoupon, Order, Payment)를 JPA로 구현해줘. 연관관계는 모두 LAZY 로딩으로 설정하고, Setter 대신 비즈니스 메서드를 사용해줘."

⚡ 2단계: 핵심 비즈니스 로직 (시나리오 1, 2, 5)
목표: 동시성 제어와 결제 프로세스, 할인 정책을 구현합니다.

가상 결제 구현 (시나리오 5): "PaymentGateway 인터페이스와 10% 실패 확률을 가진 VirtualPaymentGateway를 만들어줘."

할인 전략 패턴 (시나리오 2): "DiscountPolicy 인터페이스를 만들고 정액/정률 할인 클래스를 구현해줘."

선착순 및 주문 로직 (시나리오 1): "ItemRepository에 비관적 락(@Lock)을 적용하고, OrderService에서 재고 차감 및 결제 검증이 포함된 주문 생성 로직을 짜줘."

🔍 3단계: 조회 최적화 (시나리오 3, 4)
목표: 검색 성능을 높이고 N+1 문제를 해결합니다.

QueryDSL 설정: "QueryDSL Q클래스 생성 설정과 ItemRepositoryCustom을 통해 카테고리/가격 동적 검색 기능을 구현해줘."

조회 최적화: "주문 내역 조회 시 Fetch Join을 적용한 메서드를 만들고, application.yml에 default_batch_fetch_size: 100 설정을 추가해줘."

🧪 4단계: 검증 테스트 (마무리)
목표: 실제로 동시성이 해결되었는지 눈으로 확인합니다.

동시성 통합 테스트: "CountDownLatch를 사용해서 100명이 동시에 10개 남은 상품을 구매할 때, 정확히 10명만 성공하고 재고가 0이 되는지 확인하는 테스트 코드를 짜줘."
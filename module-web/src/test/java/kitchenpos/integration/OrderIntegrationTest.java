package kitchenpos.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;
import kitchenpos.application.OrderService;
import kitchenpos.fixture.CustomParameterizedTest;
import kitchenpos.domain.menu.Menu;
import kitchenpos.domain.menugroup.MenuGroup;
import kitchenpos.domain.order.Order;
import kitchenpos.domain.order.OrderStatus;
import kitchenpos.domain.orderedmenu.OrderedMenu;
import kitchenpos.domain.orderedmenu.OrderedMenuRepository;
import kitchenpos.domain.orderlineitem.OrderLineItem;
import kitchenpos.domain.orderlineitem.OrderLineItemRepository;
import kitchenpos.domain.ordertable.OrderTable;
import kitchenpos.domain.price.Price;
import kitchenpos.domain.quantity.Quantity;
import kitchenpos.dto.order.OrderRequest;
import kitchenpos.dto.order.OrderResponse;
import kitchenpos.dto.orderlineitem.OrderLineItemRequest;
import kitchenpos.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@DisplayName("Order 통합테스트")
class OrderIntegrationTest extends IntegrationTest {

    private static final String API_PATH = "/api/orders";

    @Autowired
    private OrderLineItemRepository orderLineItemRepository;

    @Autowired
    private OrderedMenuRepository orderedMenuRepository;

    @Autowired
    private OrderService orderService;

    private Menu menu;
    private OrderTable orderTable;

    @Override
    @BeforeEach
    void setUp() {
        super.setUp();

        saveMenu();
        saveOrderTable();
    }

    private void saveMenu() {
        final MenuGroup menuGroup = MenuGroup을_저장한다("추천메뉴");
        menu = Menu를_저장한다("양념치킨", 17_000, menuGroup);
    }

    private void saveOrderTable() {
        orderTable = OrderTable을_저장한다(null, 0, true);
    }

    @DisplayName("생성 - 성공")
    @Test
    void create_Success() throws Exception {
        // given
        final long quantityValue = 1L;
        final OrderRequest orderRequest = OrderRequest를_생성한다(orderTable.getId(), menu.getId(), quantityValue);

        // when
        // then
        mockMvc.perform(post(API_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(orderRequest)))
            .andExpect(status().isCreated())
            .andExpect(header().exists(LOCATION))
            .andExpect(header().string(CONTENT_TYPE_NAME, RESPONSE_CONTENT_TYPE))
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.orderTableId").value(orderTable.getId()))
            .andExpect(jsonPath("$.orderStatus").value(OrderStatus.COOKING.name()))
            .andExpect(jsonPath("$.orderedTime").isNotEmpty())
            .andExpect(jsonPath("$.orderLineItems.length()").value(1))
            .andExpect(jsonPath("$.orderLineItems[0].seq").isNumber())
            .andExpect(jsonPath("$.orderLineItems[0].orderId").isNumber())
            .andExpect(jsonPath("$.orderLineItems[0].menuId").value(menu.getId()))
            .andExpect(jsonPath("$.orderLineItems[0].quantity").value(quantityValue))
        ;

        final List<Order> foundOrders = orderRepository.findAll();
        assertThat(foundOrders).hasSize(1);

        final Order foundOrder = foundOrders.get(0);
        assertThat(foundOrder.getOrderTableId()).isEqualTo(orderTable.getId());
        assertThat(foundOrder.getOrderStatus()).isEqualTo(OrderStatus.COOKING);
        assertThat(foundOrder.getOrderedTime()).isNotNull();

        final List<OrderLineItem> foundOrderLineItems = orderLineItemRepository.findAllByOrder(foundOrder);
        assertThat(foundOrderLineItems).hasSize(1);

        final OrderLineItem foundOrderLineItem = foundOrderLineItems.get(0);
        assertThat(foundOrderLineItem.getOrderId()).isEqualTo(foundOrder.getId());
        assertThat(foundOrderLineItem.getOrderedMenu().getName()).isEqualTo(menu.getName());
        assertThat(foundOrderLineItem.getOrderedMenu().getPrice()).isEqualTo(menu.getPrice());
        assertThat(foundOrderLineItem.getQuantity()).isEqualTo(new Quantity(quantityValue));
    }

    protected List<OrderLineItemRequest> OrderLineItemRequests를_생성한다_사이즈_1(Long menuId, Long quantityValue) {
        return Collections.singletonList(
            new OrderLineItemRequest(menuId, quantityValue)
        );
    }

    @DisplayName("생성 - 실패 - 요청 매개변수 Order의 OrderLineItems가 empty일 때")
    @Test
    void create_Fail_When_RequestOrderLineItemsIsEmpty() throws Exception {
        // given
        final OrderRequest orderRequest = new OrderRequest(orderTable.getId(), Collections.emptyList());

        // when
        // then
        생성을_실패한다(orderRequest);
    }

    @DisplayName("생성 - 실패 - 요청 매개변수 Order의 OrderLineItems의 Menu들중에 DB에 존재하지 않는것이 있을 때")
    @Test
    void create_Fail_When_SomeRequestMenusInOrderLineItemsNotExistsInDB() throws Exception {
        // given
        final OrderRequest orderRequest = OrderRequest를_생성한다(orderTable.getId(), 0L, 1L);

        // when
        // then
        생성을_실패한다(orderRequest);
    }

    @DisplayName("생성 - 실패 - 요청 매개변수 Order의 OrderTableId가 DB에 존재하지 않을 때")
    @Test
    void create_Fail_When_RequestOrderTableIdOfOrderNotExistsInDB() throws Exception {
        // given
        final OrderRequest orderRequest = OrderRequest를_생성한다(0L, menu.getId(), 1L);

        // when
        // then
        생성을_실패한다(orderRequest);
    }

    @DisplayName("생성 - 실패 - DB에서 조회한 OrderTable이 비어있을 때")
    @Test
    void create_Fail_When_OrderTableFromDBIsEmpty() throws Exception {
        // given
        orderTable.changeEmpty(true);
        final OrderRequest orderRequest = OrderRequest를_생성한다(0L, menu.getId(), 1L);

        // when
        // then
        생성을_실패한다(orderRequest);
    }

    @DisplayName("모든 Order들 조회 - 성공")
    @Test
    void findAll_Success() throws Exception {
        // given
        final Order order = Order를_저장한다(orderTable, OrderStatus.COOKING);
        final OrderLineItem orderLineItem = OrderLineItem을_저장한다(order);

        // when
        // then
        mockMvc.perform(get(API_PATH))
            .andExpect(status().isOk())
            .andExpect(header().string(CONTENT_TYPE_NAME, RESPONSE_CONTENT_TYPE))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").isNumber())
            .andExpect(jsonPath("$[0].orderTableId").value(orderTable.getId()))
            .andExpect(jsonPath("$[0].orderStatus").value(order.getOrderStatus().name()))
            .andExpect(jsonPath("$[0].orderedTime").isNotEmpty())
            .andExpect(jsonPath("$[0].orderLineItems.length()").value(1))
            .andExpect(jsonPath("$[0].orderLineItems[0].seq").isNumber())
            .andExpect(jsonPath("$[0].orderLineItems[0].orderId").value(order.getId()))
            .andExpect(jsonPath("$[0].orderLineItems[0].menuId").value(menu.getId()))
            .andExpect(jsonPath("$[0].orderLineItems[0].quantity").value(orderLineItem.getQuantityValue()))
        ;
    }

    @DisplayName("Order의 OrderStatus 변경 - 성공")
    @CustomParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"MEAL", "COMPLETION", "COOKING"})
    void changeOrderStatus_Success(OrderStatus newOrderStatus) throws Exception {
        // given
        final Order order = Order를_저장한다(orderTable, OrderStatus.COOKING);
        final OrderLineItem orderLineItem = OrderLineItem을_저장한다(order);

        final OrderRequest orderRequest = new OrderRequest(newOrderStatus);

        // when
        // then
        mockMvc.perform(put(API_PATH + "/" + order.getId() + "/order-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(orderRequest)))
            .andExpect(status().isOk())
            .andExpect(header().string(CONTENT_TYPE_NAME, RESPONSE_CONTENT_TYPE))
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.orderTableId").value(orderTable.getId()))
            .andExpect(jsonPath("$.orderStatus").value(newOrderStatus.name()))
            .andExpect(jsonPath("$.orderedTime").isNotEmpty())
            .andExpect(jsonPath("$.orderLineItems.length()").value(1))
            .andExpect(jsonPath("$.orderLineItems[0].seq").isNumber())
            .andExpect(jsonPath("$.orderLineItems[0].orderId").value(order.getId()))
            .andExpect(jsonPath("$.orderLineItems[0].menuId").value(menu.getId()))
            .andExpect(jsonPath("$.orderLineItems[0].quantity").value(orderLineItem.getQuantityValue()))
        ;

        final Order foundOrder = findOrderById(order.getId());
        assertThat(foundOrder.getOrderStatus()).isEqualTo(newOrderStatus);
    }

    @DisplayName("Order의 OrderStatus 변경 - 실패 - 기존에 COMPLETION일 때")
    @CustomParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"MEAL", "COMPLETION", "COOKING"})
    void changeOrderStatus_Fail_When_OldOrderStatusIsCOMPLETION(OrderStatus newOrderStatus) throws Exception {
        // given
        final Order order = Order를_저장한다(orderTable, OrderStatus.COMPLETION);
        OrderLineItem을_저장한다(order);

        final OrderRequest orderRequest = new OrderRequest(newOrderStatus);

        // when
        // then
        PUT_API를_요청하면_BadRequest를_응답한다(API_PATH + "/" + order.getId() + "/order-status", orderRequest);

        final Order foundOrder = findOrderById(order.getId());
        assertThat(foundOrder.getOrderStatus()).isEqualTo(OrderStatus.COMPLETION);
    }

    @DisplayName("Order의 OrderStatus 변경 - 실패 - Order이 DB에 존재하지 않을 때")
    @CustomParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"MEAL", "COMPLETION", "COOKING"})
    void changeOrderStatus_Fail_When_OrderNotExistsInDB(OrderStatus newOrderStatus) throws Exception {
        // given
        final OrderRequest orderRequest = new OrderRequest(newOrderStatus);

        // when
        // then
        PUT_API를_요청하면_BadRequest를_응답한다(API_PATH + "/0/order-status", orderRequest);
    }

    @DisplayName("Menu의 이름과 가격을 변경해도, 변경 이전에 저장된 주문 항목이 변경되지 않는다.")
    @Test
    void notChangeOrderedMenu_After_ModifyMenu() {
        // given
        final String oldMenuName = menu.getName();
        final Price oldMenuPrice = new Price(menu.getPriceAsInt());

        final OrderRequest orderRequest = OrderRequest를_생성한다(orderTable.getId(), menu.getId(), 1L);
        final OrderResponse orderResponse = orderService.create(orderRequest);

        // when
        final String newMenuName = "새로운메뉴이름";
        final int newPriceValue = 1_111_111;
        메뉴의_정보를_수정한다(newMenuName, newPriceValue);

        // then
        final OrderedMenu foundOrderedMenu = OrderedMenu를_조회한다(orderResponse.getId());
        assertThat(foundOrderedMenu.getMenuId()).isEqualTo(menu.getId());
        assertThat(foundOrderedMenu.getName()).isEqualTo(oldMenuName);
        assertThat(foundOrderedMenu.getPrice()).isEqualTo(oldMenuPrice);

        final Menu foundMenu = Menu를_조회한다(menu.getId());
        assertThat(foundMenu.getName()).isEqualTo(newMenuName);
        assertThat(foundMenu.getPriceAsInt()).isEqualTo(newPriceValue);
    }

    private Menu Menu를_조회한다(Long id) {
        return menuRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("해당 Id의 Menu가 존재하지 않습니다."));
    }

    private OrderedMenu OrderedMenu를_조회한다(Long orderId) {
        final Order foundOrder = findOrderById(orderId);
        final List<OrderLineItem> foundOrderLineItems = orderLineItemRepository.findAllByOrder(foundOrder);

        assertThat(foundOrderLineItems).hasSize(1);
        final OrderLineItem foundOrderLineItem = foundOrderLineItems.get(0);

        return foundOrderLineItem.getOrderedMenu();
    }

    private void 메뉴의_정보를_수정한다(String newName, int newPrice) {
        menu.changeName(newName);
        menu.changePrice(newPrice);
        resetEntityManager();
    }

    private void 생성을_실패한다(OrderRequest orderRequest) throws Exception {
        POST_API를_요청하면_BadRequest를_응답한다(API_PATH, orderRequest);

        Repository가_비어있다(orderRepository);
        Repository가_비어있다(orderLineItemRepository);
    }

    private OrderRequest OrderRequest를_생성한다(Long orderTableId, Long menuId, Long quantityValue) {
        final List<OrderLineItemRequest> orderLineItemRequests = OrderLineItemRequests를_생성한다_사이즈_1(menuId, quantityValue);
        return new OrderRequest(orderTableId, orderLineItemRequests);
    }

    private OrderLineItem OrderLineItem을_저장한다(Order order) {
        final OrderedMenu orderedMenu = new OrderedMenu(menu.getId(), menu.getName(), menu.getPrice());
        orderedMenuRepository.save(orderedMenu);
        return orderLineItemRepository.save(new OrderLineItem(order, orderedMenu, 1L));
    }

    private Order findOrderById(Long id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("해당 id의 Order이 존재하지 않습니다."));
    }
}

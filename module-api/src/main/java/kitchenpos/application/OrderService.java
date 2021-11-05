package kitchenpos.application;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import kitchenpos.domain.menu.Menu;
import kitchenpos.domain.menu.MenuRepository;
import kitchenpos.domain.order.Order;
import kitchenpos.domain.order.OrderRepository;
import kitchenpos.domain.order.OrderStatus;
import kitchenpos.domain.orderedmenu.OrderedMenu;
import kitchenpos.domain.orderedmenu.OrderedMenuRepository;
import kitchenpos.domain.orderlineitem.OrderLineItem;
import kitchenpos.domain.orderlineitem.OrderLineItemRepository;
import kitchenpos.domain.orderlineitem.OrderLineItems;
import kitchenpos.domain.ordertable.OrderTable;
import kitchenpos.domain.ordertable.OrderTableRepository;
import kitchenpos.domain.quantity.Quantity;
import kitchenpos.dto.order.OrderRequest;
import kitchenpos.dto.order.OrderResponse;
import kitchenpos.dto.orderlineitem.OrderLineItemRequest;
import kitchenpos.dto.orderlineitem.OrderLineItemResponse;
import kitchenpos.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final MenuRepository menuRepository;
    private final OrderRepository orderRepository;
    private final OrderLineItemRepository orderLineItemRepository;
    private final OrderedMenuRepository orderedMenuRepository;
    private final OrderTableRepository orderTableRepository;

    public OrderService(
        final MenuRepository menuRepository,
        final OrderRepository orderRepository,
        final OrderLineItemRepository orderLineItemRepository,
        final OrderedMenuRepository orderedMenuRepository,
        final OrderTableRepository orderTableRepository
    ) {
        this.menuRepository = menuRepository;
        this.orderRepository = orderRepository;
        this.orderLineItemRepository = orderLineItemRepository;
        this.orderedMenuRepository = orderedMenuRepository;
        this.orderTableRepository = orderTableRepository;
    }

    @Transactional
    public OrderResponse create(final OrderRequest orderRequest) {
        final OrderLineItems orderLineItems = convertToOrderLineItems(orderRequest.getOrderLineItems());
        orderLineItems.validateNotEmpty();

        final OrderTable foundOrderTable = findOrderTableById(orderRequest.getOrderTableId());
        final Order order = new Order(foundOrderTable, OrderStatus.COOKING);
        orderRepository.save(order);

        orderLineItems.assignOrder(order);
        orderLineItemRepository.saveAll(orderLineItems.getOrderLineItems());

        return convertToOrderResponse(order, orderLineItems.getOrderLineItems());
    }

    private OrderResponse convertToOrderResponse(Order order, List<OrderLineItem> orderLineItems) {
        List<OrderLineItemResponse> orderLineItemResponses = convertToOrderResponses(orderLineItems);
        return new OrderResponse(order, orderLineItemResponses);
    }

    private List<OrderLineItemResponse> convertToOrderResponses(List<OrderLineItem> orderLineItems) {
        return orderLineItems.stream()
            .map(OrderLineItemResponse::new)
            .collect(Collectors.toList())
            ;
    }

    private OrderTable findOrderTableById(Long orderTableId) {
        return orderTableRepository.findById(orderTableId)
            .orElseThrow(() -> new NotFoundException("해당 id의 OrderTable이 존재하지 않습니다."));
    }

    private OrderLineItems convertToOrderLineItems(List<OrderLineItemRequest> orderLineItemRequests) {
        final OrderLineItems orderLineItems = new OrderLineItems();
        for (OrderLineItemRequest orderLineItemRequest : orderLineItemRequests) {
            final OrderedMenu orderedMenu = getNewSavedOrderedMenu(orderLineItemRequest);
            final Long quantityValue = orderLineItemRequest.getQuantity();
            final OrderLineItem orderLineItem = new OrderLineItem(orderedMenu, new Quantity(quantityValue));
            orderLineItems.add(orderLineItem);
        }
        return orderLineItems;
    }

    private OrderedMenu getNewSavedOrderedMenu(OrderLineItemRequest orderLineItemRequest) {
        final Menu foundMenu = findMenuById(orderLineItemRequest.getMenuId());
        final OrderedMenu orderedMenu = new OrderedMenu(foundMenu.getId(), foundMenu.getName(), foundMenu.getPrice());
        return orderedMenuRepository.save(orderedMenu);
    }

    private Menu findMenuById(Long menuId) {
        return menuRepository.findById(menuId)
            .orElseThrow(() -> new NotFoundException("해당 id의 Menu가 존재하지 않습니다."));
    }

    public List<OrderResponse> findAll() {
        final List<OrderResponse> orderResponses = new ArrayList<>();
        final List<Order> foundAllOrders = orderRepository.findAll();
        for (Order foundOrder : foundAllOrders) {
            final List<OrderLineItem> foundOrderLineItems = orderLineItemRepository.findAllByOrder(foundOrder);
            final List<OrderLineItemResponse> orderLineItemResponses = convertToOrderResponses(foundOrderLineItems);
            final OrderResponse orderResponse = new OrderResponse(foundOrder, orderLineItemResponses);
            orderResponses.add(orderResponse);
        }
        return orderResponses;
    }

    @Transactional
    public OrderResponse changeOrderStatus(final Long orderId, final OrderRequest orderRequest) {
        final Order foundOrder = findOrderById(orderId);
        foundOrder.validateNotCompleted();
        foundOrder.changeStatus(orderRequest.getOrderStatus());

        final List<OrderLineItem> foundOrderLineItems = orderLineItemRepository.findAllByOrder(foundOrder);
        return convertToOrderResponse(foundOrder, foundOrderLineItems);
    }

    private Order findOrderById(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new NotFoundException("해당 id의 Order가 존재하지 않습니다."));
    }
}

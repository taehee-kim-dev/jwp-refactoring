package kitchenpos.dto.tablegroup;

import java.util.List;
import kitchenpos.dto.ordertable.OrderTableRequest;

public class TableGroupRequest {

    private final List<OrderTableRequest> orderTables;

    public TableGroupRequest() {
        this(null);
    }

    public TableGroupRequest(List<OrderTableRequest> orderTables) {
        this.orderTables = orderTables;
    }

    public List<OrderTableRequest> getOrderTables() {
        return orderTables;
    }
}

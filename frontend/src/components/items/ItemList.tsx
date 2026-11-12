/**
 * ItemList.tsx
 * Responsibility: render the stock table (one column per contract response field).
 * Props: items. API calls: none (pages fetch, components render).
 * Contract note: negative balances are shown plainly, never suppressed.
 */

import { Link } from 'react-router-dom'
import type { Item } from '../../types'

interface Props {
  items: Item[]
}

export default function ItemList({ items }: Props) {
  return (
    <div className="table-wrap"><table className="stock-table">
      <thead>
        <tr>
          <th>Item</th>
          <th>Unit</th>
          <th>Confirmed stock</th>
          <th>Planned outstanding</th>
          <th>History</th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr key={item.id} className={item.balance < 0 ? 'negative-row' : ''}>
            <td data-label="Item"><strong>{item.name}</strong><span className="item-meta">Inventory item</span></td>
            <td data-label="Unit">{item.unit}</td>
            <td data-label="Confirmed stock">
              <span className="stock-number">{item.balance}</span>
              {item.balance < 0 ? <span className="badge danger">Needs review</span> : <span className="badge neutral">Confirmed</span>}
            </td>
            <td data-label="Planned outstanding">{item.planned_outstanding}</td>
            <td data-label="History">
              <Link className="text-link" to={`/items/${item.id}/history`}>View history <span aria-hidden="true">↗</span></Link>
            </td>
          </tr>
        ))}
      </tbody>
    </table></div>
  )
}

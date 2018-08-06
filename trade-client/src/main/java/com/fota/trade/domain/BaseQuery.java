// Copyright(c) 2018 The fortuna developers
// This file is part of the fortuna.
//
// fortuna is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// fortuna is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the fortuna. If not, see <http://www.gnu.org/licenses/>.

package com.fota.trade.domain;

import com.fota.common.Query;
import lombok.Data;

import java.util.List;

/**
 * create on 16/07/2018
 *
 * @author JASON.TAO
 */
@Data
public class BaseQuery extends Query {
    public Integer startRow;
    public Integer endRow;
    public Long userId;
    public Integer sourceId;
    public List<Integer> orderStatus;
}

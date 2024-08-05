import React, { ReactNode } from 'react';
import { AGG_TYPE_MAP, PREFIX_CLS } from '../../common/constants';
import { ChatContextType, DateInfoType, EntityInfoType, FilterItemType } from '../../common/type';
import { Button, DatePicker, Row, Col } from 'antd';
import { CheckCircleFilled, CloseCircleFilled, ReloadOutlined } from '@ant-design/icons';
import Loading from './Loading';
import FilterItem from './FilterItem';
import MarkDown from '../ChatMsg/MarkDown';
import classNames from 'classnames';
import { isMobile } from '../../utils/utils';
import dayjs, { Dayjs } from 'dayjs';
import locale from 'antd/locale/zh_CN';
import quarterOfYear from 'dayjs/plugin/quarterOfYear';

import 'dayjs/locale/zh-cn';

dayjs.extend(quarterOfYear);
dayjs.locale('zh-cn');

const { RangePicker } = DatePicker;

type Props = {
  parseLoading: boolean;
  parseInfoOptions: ChatContextType[];
  parseTip: string;
  currentParseInfo?: ChatContextType;
  agentId?: number;
  dimensionFilters: FilterItemType[];
  dateInfo: DateInfoType;
  entityInfo: EntityInfoType;
  integrateSystem?: string;
  parseTimeCost?: number;
  isDeveloper?: boolean;
  isSimpleMode?: boolean;
  onSelectParseInfo: (parseInfo: ChatContextType) => void;
  onSwitchEntity: (entityId: string) => void;
  onFiltersChange: (filters: FilterItemType[]) => void;
  onDateInfoChange: (dateRange: any) => void;
  onRefresh: () => void;
  handlePresetClick: any;
};

const MAX_OPTION_VALUES_COUNT = 2;

type RangeValue = [Dayjs, Dayjs];
type RangeKeys = '近7日' | '近14日' | '近30日' | '本周' | '本月' | '上月' | '本季度' | '本年';

const ParseTip: React.FC<Props> = ({
  parseLoading,
  parseInfoOptions,
  parseTip,
  currentParseInfo,
  agentId,
  dimensionFilters,
  dateInfo,
  entityInfo,
  integrateSystem,
  parseTimeCost,
  isDeveloper,
  isSimpleMode,
  onSelectParseInfo,
  onSwitchEntity,
  onFiltersChange,
  onDateInfoChange,
  onRefresh,
  handlePresetClick,
}) => {
  const ranges: Record<RangeKeys, RangeValue> = {
    近7日: [dayjs().subtract(7, 'day'), dayjs()],
    近14日: [dayjs().subtract(14, 'day'), dayjs()],
    近30日: [dayjs().subtract(30, 'day'), dayjs()],
    本周: [dayjs().startOf('week'), dayjs().endOf('week')],
    本月: [dayjs().startOf('month'), dayjs().endOf('month')],
    上月: [
      dayjs().subtract(1, 'month').startOf('month'),
      dayjs().subtract(1, 'month').endOf('month'),
    ],
    本季度: [dayjs().startOf('quarter'), dayjs().endOf('quarter')], // 使用 quarterOfYear 插件
    本年: [dayjs().startOf('year'), dayjs().endOf('year')],
  };

  const prefixCls = `${PREFIX_CLS}-item`;

  const getNode = (tipTitle: ReactNode, tipNode?: ReactNode, failed?: boolean) => {
    return (
      <div className={`${prefixCls}-parse-tip`}>
        <div className={`${prefixCls}-title-bar`}>
          {!failed ? (
            <CheckCircleFilled className={`${prefixCls}-step-icon`} />
          ) : (
            <CloseCircleFilled className={`${prefixCls}-step-error-icon`} />
          )}
          <div className={`${prefixCls}-step-title`}>
            {tipTitle}
            {tipNode === undefined && <Loading />}
          </div>
        </div>
        {(tipNode || tipNode === null) && (
          <div
            className={classNames(
              `${prefixCls}-content-container`,
              tipNode === null && `${prefixCls}-empty-content-container`,
              failed && `${prefixCls}-content-container-failed`
            )}
          >
            {tipNode}
          </div>
        )}
      </div>
    );
  };

  if (parseLoading) {
    return getNode('意图解析中');
  }

  if (parseTip) {
    return getNode(
      <>
        意图解析失败
        {parseTimeCost && isDeveloper && (
          <span className={`${prefixCls}-title-tip`}>(耗时: {parseTimeCost}ms)</span>
        )}
      </>,
      parseTip,
      true
    );
  }

  if (parseInfoOptions.length === 0) {
    return null;
  }

  const {
    modelId,
    dataSet,
    dimensions,
    metrics,
    aggType,
    queryMode,
    queryType,
    properties,
    entity,
    elementMatches,
    nativeQuery,
    textInfo = '',
  } = currentParseInfo || {};

  const entityAlias = entity?.alias?.[0]?.split('.')?.[0];

  const getTipNode = () => {
    const dimensionItems = dimensions?.filter(item => item.type === 'DIMENSION');
    const itemValueClass = `${prefixCls}-tip-item-value`;
    const entityId = dimensionFilters?.length > 0 ? dimensionFilters[0].value : undefined;
    const entityAlias = entity?.alias?.[0]?.split('.')?.[0];
    const entityName = elementMatches?.find(item => item.element?.type === 'ID')?.element.name;

    const { type: agentType, name: agentName } = properties || {};

    const fields =
      queryMode === 'TAG_DETAIL' ? dimensionItems?.concat(metrics || []) : dimensionItems;

    return (
      <div className={`${prefixCls}-tip-content`}>
        {!!agentType && queryMode !== 'LLM_S2SQL' ? (
          <div className={`${prefixCls}-tip-item`}>
            将由{agentType === 'plugin' ? '插件' : '内置'}工具
            <span className={itemValueClass}>{agentName}</span>来解答
          </div>
        ) : (
          <>
            {(queryMode?.includes('ENTITY') || queryMode === 'LLM_S2SQL') &&
            typeof entityId === 'string' &&
            !!entityAlias &&
            !!entityName ? (
              <div className={`${prefixCls}-tip-item`}>
                <div className={`${prefixCls}-tip-item-name`}>{entityAlias}：</div>
                <div className={itemValueClass}>{entityName}</div>
              </div>
            ) : (
              <div className={`${prefixCls}-tip-item`}>
                <div className={`${prefixCls}-tip-item-name`}>数据集：</div>
                <div className={itemValueClass}>{dataSet?.name}</div>
              </div>
            )}
            {(queryType === 'METRIC' || queryType === 'METRIC_TAG' || queryType === 'DETAIL') && (
              <div className={`${prefixCls}-tip-item`}>
                <div className={`${prefixCls}-tip-item-name`}>查询模式：</div>
                <div className={itemValueClass}>
                  {queryType === 'METRIC' || queryType === 'METRIC_TAG' ? '指标模式' : '明细模式'}
                </div>
              </div>
            )}
            {queryType !== 'DETAIL' &&
              metrics &&
              metrics.length > 0 &&
              !dimensions?.some(item => item.bizName?.includes('_id')) && (
                <div className={`${prefixCls}-tip-item`}>
                  <div className={`${prefixCls}-tip-item-name`}>指标：</div>
                  <div className={itemValueClass}>
                    {queryType === 'METRIC' || queryType === 'ID'
                      ? metrics[0].name
                      : metrics.map(metric => metric.name).join('、')}
                  </div>
                </div>
              )}
            {['METRIC_GROUPBY', 'METRIC_ORDERBY', 'TAG_DETAIL', 'LLM_S2SQL'].includes(queryMode!) &&
              fields &&
              fields.length > 0 && (
                <div className={`${prefixCls}-tip-item`}>
                  <div className={`${prefixCls}-tip-item-name`}>
                    {queryType === 'DETAIL' ? '查询字段' : '下钻维度'}：
                  </div>
                  <div className={itemValueClass}>
                    {fields
                      .slice(0, MAX_OPTION_VALUES_COUNT)
                      .map(field => field.name)
                      .join('、')}
                    {fields.length > MAX_OPTION_VALUES_COUNT && '...'}
                  </div>
                </div>
              )}
            {queryMode !== 'TAG_ID' &&
              !dimensions?.some(item => item.bizName?.includes('_id')) &&
              entityInfo?.dimensions
                ?.filter(dimension => dimension.value != null)
                .map(dimension => (
                  <div className={`${prefixCls}-tip-item`} key={dimension.itemId}>
                    <div className={`${prefixCls}-tip-item-name`}>{dimension.name}：</div>
                    <div className={itemValueClass}>{dimension.value}</div>
                  </div>
                ))}
            {(queryMode === 'METRIC_ORDERBY' || queryMode === 'METRIC_MODEL') &&
              aggType &&
              aggType !== 'NONE' && (
                <div className={`${prefixCls}-tip-item`}>
                  <div className={`${prefixCls}-tip-item-name`}>聚合方式：</div>
                  <div className={itemValueClass}>{AGG_TYPE_MAP[aggType]}</div>
                </div>
              )}
          </>
        )}
      </div>
    );
  };

  const getFilterContent = (filters: any) => {
    const itemValueClass = `${prefixCls}-tip-item-value`;
    const { startDate, endDate } = dateInfo || {};
    const tipItemOptionClass = classNames(`${prefixCls}-tip-item-option`, {
      [`${prefixCls}-mobile-tip-item-option`]: isMobile,
    });
    return (
      <div className={`${prefixCls}-tip-item-filter-content`}>
        <div className={tipItemOptionClass}>
          <span className={`${prefixCls}-tip-item-filter-name`}>数据时间：</span>
          {nativeQuery ? (
            <span className={itemValueClass}>
              {startDate === endDate ? startDate : `${startDate} ~ ${endDate}`}
            </span>
          ) : (
            <RangePicker
              value={[dayjs(startDate), dayjs(endDate)]}
              onChange={onDateInfoChange}
              format="YYYY-MM-DD"
              renderExtraFooter={() => (
                <Row gutter={[28, 28]}>
                  {Object.keys(ranges).map(key => (
                    <Col key={key}>
                      <Button
                        size="small"
                        onClick={() => handlePresetClick(ranges[key as RangeKeys])}
                      >
                        {key}
                      </Button>
                    </Col>
                  ))}
                </Row>
              )}
            />
          )}
        </div>
        {filters?.map((filter: any, index: number) => (
          <FilterItem
            modelId={modelId!}
            filters={dimensionFilters}
            filter={filter}
            index={index}
            chatContext={currentParseInfo!}
            entityAlias={entityAlias}
            agentId={agentId}
            integrateSystem={integrateSystem}
            onFiltersChange={onFiltersChange}
            onSwitchEntity={onSwitchEntity}
            key={`${filter.name}_${index}`}
          />
        ))}
      </div>
    );
  };

  const getFiltersNode = () => {
    return (
      <>
        <div className={`${prefixCls}-tip-item`}>
          <div className={`${prefixCls}-tip-item-name`}>筛选条件：</div>
          <div className={`${prefixCls}-tip-item-content`}>
            {getFilterContent(dimensionFilters)}
          </div>
        </div>
        <Button className={`${prefixCls}-reload`} size="small" onClick={onRefresh}>
          <ReloadOutlined />
          重新查询
        </Button>
      </>
    );
  };

  const { type: agentType } = properties || {};

  const tipNode = (
    <div className={`${prefixCls}-tip`}>
      {getTipNode()}
      {!(!!agentType && queryMode !== 'LLM_S2SQL') && getFiltersNode()}
    </div>
  );

  return getNode(
    <div className={`${prefixCls}-title-bar`}>
      <div>
        意图解析
        {parseTimeCost && isDeveloper && (
          <span className={`${prefixCls}-title-tip`}>(耗时: {parseTimeCost}ms)</span>
        )}
        {parseInfoOptions?.length > 1 ? '：' : ''}
      </div>
      {!isSimpleMode && parseInfoOptions?.length > 1 && (
        <div className={`${prefixCls}-content-options`}>
          {parseInfoOptions.map((parseInfo, index) => (
            <div
              className={`${prefixCls}-content-option ${
                parseInfo.id === currentParseInfo?.id ? `${prefixCls}-content-option-active` : ''
              }`}
              onClick={() => {
                onSelectParseInfo(parseInfo);
              }}
              key={parseInfo.id}
            >
              解析{index + 1}
            </div>
          ))}
        </div>
      )}
    </div>,
    isSimpleMode ? <MarkDown markdown={textInfo} /> : queryMode === 'PLAIN_TEXT' ? null : tipNode
  );
};

export default ParseTip;

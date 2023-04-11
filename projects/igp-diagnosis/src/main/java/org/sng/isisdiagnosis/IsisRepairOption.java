package org.sng.isisdiagnosis;

import java.util.List;

public class IsisRepairOption {

    public final String _errorType;
    public final List<IsisError> _isisErrors;

    public IsisRepairOption(String errorType, List<IsisError> isisErrors) {
        _errorType = errorType;
        _isisErrors = isisErrors;
    }

    // 更具体的错误原因以及对应的配置行
    public static class IsisError{
        private final String _errorReason;
        private final List<ErrorConfig> _errorConfigs;

        public IsisError(String errorReason,List<ErrorConfig> errorConfigs) {
            _errorReason = errorReason;
            _errorConfigs = errorConfigs;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("{");
            sb.append("\"_errorReason\":\"")
                    .append(_errorReason).append('\"');
            sb.append(",\"_errorConfigs\":")
                    .append(_errorConfigs);
            sb.append('}');
            return sb.toString();
        }
    }

    public static class ErrorConfig{
        public final String _deviceName;
        public final Integer _lineNumber;
        public final String _config;

        public ErrorConfig(String deviceName, Integer lineNumber, String config) {
            _deviceName = deviceName;
            _lineNumber = lineNumber;
            _config = config;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("{");
            sb.append("\"_deviceName\":\"")
                    .append(_deviceName).append('\"');
            sb.append(",\"_lineNumber\":")
                    .append(_lineNumber);
            sb.append(",\"_config\":\"")
                    .append(_config).append('\"');
            sb.append('}');
            return sb.toString();
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append("\"_errorType\":\"")
                .append(_errorType).append('\"');
        sb.append(",\"_isisErrors\":")
                .append(_isisErrors);
        sb.append('}');
        return sb.toString();
    }
}

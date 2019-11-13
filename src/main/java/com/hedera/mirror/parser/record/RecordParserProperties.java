package com.hedera.mirror.parser.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import java.nio.file.Path;
import java.time.Duration;
import javax.inject.Named;
import javax.validation.constraints.NotNull;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.hedera.mirror.MirrorProperties;
import com.hedera.mirror.domain.StreamType;
import com.hedera.mirror.parser.ParserProperties;

@Data
@Named
@Validated
@ConfigurationProperties("hedera.mirror.parser.record")
public class RecordParserProperties implements ParserProperties {

    private final MirrorProperties mirrorProperties;

    private boolean enabled = true;

    @NotNull
    private Duration frequency = Duration.ofMillis(500L);

    private boolean persistClaims = false;

    private boolean persistContracts = true;

    private boolean persistCryptoTransferAmounts = true;

    private boolean persistFiles = true;

    private boolean persistSystemFiles = true;

    public Path getStreamPath() {
        return mirrorProperties.getDataPath().resolve(getStreamType().getPath());
    }

    @Override
    public StreamType getStreamType() {
        return StreamType.RECORD;
    }
}

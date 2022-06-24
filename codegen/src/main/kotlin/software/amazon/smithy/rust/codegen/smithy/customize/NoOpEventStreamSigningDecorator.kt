/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.rust.codegen.smithy.customize

import software.amazon.smithy.rust.codegen.rustlang.CargoDependency
import software.amazon.smithy.rust.codegen.rustlang.Writable
import software.amazon.smithy.rust.codegen.rustlang.rustTemplate
import software.amazon.smithy.rust.codegen.rustlang.writable
import software.amazon.smithy.rust.codegen.smithy.CodegenContext
import software.amazon.smithy.rust.codegen.smithy.RuntimeConfig
import software.amazon.smithy.rust.codegen.smithy.RuntimeType
import software.amazon.smithy.rust.codegen.smithy.generators.config.ConfigCustomization
import software.amazon.smithy.rust.codegen.smithy.generators.config.EventStreamSigningConfig
import software.amazon.smithy.rust.codegen.util.hasEventStreamOperations

/**
 * The NoOpEventStreamSigningDecorator:
 * - adds a `new_event_stream_signer()` method to `config` to create an Event Stream NoOp signer
 */
open class NoOpEventStreamSigningDecorator : RustCodegenDecorator {
    override val name: String = "EventStreamDecorator"
    override val order: Byte = 0

    private fun applies(codegenContext: CodegenContext): Boolean =
        codegenContext.serviceShape.hasEventStreamOperations(codegenContext.model)

    override fun configCustomizations(
        codegenContext: CodegenContext,
        baseCustomizations: List<ConfigCustomization>
    ): List<ConfigCustomization> {
        if (!applies(codegenContext))
            return baseCustomizations
        return baseCustomizations + NoOpEventStreamSigningConfig(
            codegenContext.runtimeConfig,
        )
    }
}

class NoOpEventStreamSigningConfig(
    runtimeConfig: RuntimeConfig,
) : EventStreamSigningConfig(runtimeConfig) {
    private val smithyEventStream = CargoDependency.SmithyEventStream(runtimeConfig)
    private val codegenScope = arrayOf(
        "NoOpSigner" to RuntimeType("NoOpSigner", smithyEventStream, "aws_smithy_eventstream::frame"),
        "SharedPropertyBag" to RuntimeType(
            "SharedPropertyBag",
            CargoDependency.SmithyHttp(runtimeConfig),
            "aws_smithy_http::property_bag"
        ),
        "SignMessage" to RuntimeType(
            "SignMessage",
            CargoDependency.SmithyEventStream(runtimeConfig),
            "aws_smithy_eventstream::frame"
        ),
    )

    override fun inner(): Writable {
        return writable {
            rustTemplate(
                """
                /// Creates a new Event Stream `SignMessage` implementor.
                pub fn new_event_stream_signer(
                    &self,
                    _properties: #{SharedPropertyBag}
                ) -> impl #{SignMessage} {
                    #{NoOpSigner}{}
                }
                """,
                *codegenScope
            )
        }
    }
}

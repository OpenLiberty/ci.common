/**
 * (C) Copyright IBM Corporation 2025
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openliberty.tools.common.ai.util;

import java.util.Set;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.markdown.MarkdownNodeRendererContext;
import org.commonmark.renderer.markdown.MarkdownNodeRendererFactory;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.commonmark.renderer.NodeRenderer;
import org.fusesource.jansi.Ansi;

import static org.fusesource.jansi.Ansi.ansi;

public class MarkdownConsoleFormatter {
	enum AnsiEscape {
		GRAY(ansi().bold().fgBrightBlack(), ansi().boldOff().fgDefault()),
		BRIGHT_BLUE(ansi().fgBrightBlue(), ansi().fgDefault()),
		BOLD(ansi().bold(), ansi().boldOff()),
		ITALIC(ansi().a(Ansi.Attribute.ITALIC), ansi().a(Ansi.Attribute.ITALIC_OFF)),
		UNDERLINE(ansi().a(Ansi.Attribute.UNDERLINE), ansi().a(Ansi.Attribute.UNDERLINE_OFF));

		public final String before;
		public final String after;

		private AnsiEscape(Ansi before, Ansi after) {
			this.before = before.toString();
			this.after = after.toString();
		}
	}

	// Will be rendered with ANSI escape codes wrapping children
	class AnsiBlock extends CustomBlock {
		private AnsiEscape escape;

		public AnsiBlock(AnsiEscape escape) {
			this.escape = escape;
		}
	}

	class AnsiNode extends CustomNode {
		private AnsiEscape escape;

		public AnsiNode(AnsiEscape escape) {
			this.escape = escape;
		}
	}

	class RawNode extends CustomNode {
		private String raw;

		public RawNode(String raw) {
			this.raw = raw;
		}
	}

	class AnsiRenderer extends AbstractVisitor implements NodeRenderer {
		private final MarkdownNodeRendererContext context;

		public AnsiRenderer(MarkdownNodeRendererContext context) {
			this.context = context;
		}

		@Override
		public Set<Class<? extends Node>> getNodeTypes() {
			return Set.of(AnsiBlock.class, AnsiNode.class, RawNode.class, CodeSection.class);
		}

		@Override
		public void render(Node node) {
			if (node instanceof RawNode rawNode) {
				context.getWriter().raw(rawNode.raw);
				return;
			}

			AnsiEscape escape = null;
			if (node instanceof AnsiBlock block) {
				escape = block.escape;
			} else if (node instanceof AnsiNode inline) {
				escape = inline.escape;
			}

			context.getWriter().raw(escape.before); // start ansi

			for (Node child = node.getFirstChild(); child != null; child = child.getNext())
				context.render(child);
			context.getWriter().raw(escape.after); // end ansi
		}
	}

	class CodeSection extends CustomBlock {

	}

	class AnsiVisitor extends AbstractVisitor {
		@Override
		public void visit(Heading heading) {
			// Make heading a child of the underline node
			Node underline = new AnsiBlock(AnsiEscape.UNDERLINE);
			heading.insertAfter(underline);
			heading.unlink();
			underline.appendChild(heading);
		}

		@Override
		public void visit(Emphasis em) {
			Node italic = new AnsiNode(AnsiEscape.ITALIC);
			em.insertAfter(italic);
			em.unlink();
			Node next, child = em.getFirstChild();
			while (child != null) {
				next = child.getNext();
				italic.appendChild(child);
				child = next;
			}
			visitChildren(italic);
		}

		@Override
		public void visit(StrongEmphasis strong) {
			Node bold = new AnsiNode(AnsiEscape.BOLD);
			strong.insertAfter(bold);
			strong.unlink();
			Node next, child = strong.getFirstChild();
			while (child != null) {
				next = child.getNext();
				bold.appendChild(child);
				child = next;
			}
			visitChildren(bold);
		}

		@Override
		public void visit(Link link) {
			Node blue = new AnsiNode(AnsiEscape.BRIGHT_BLUE);
			link.insertAfter(blue);
			link.unlink();
			blue.appendChild(link);
		}

		@Override
		public void visit(FencedCodeBlock fenceBlock) {
			if (fenceBlock.getInfo() != null && !fenceBlock.getInfo().isEmpty()) {
				String blockType = fenceBlock.getInfo().split(" ")[0];

				if (blockType.equals("java")) {
					Node section = new RawNode(JavaCodePrinter.colorKeywords(fenceBlock.getLiteral()) + "\n");
					fenceBlock.insertAfter(section);
					fenceBlock.unlink();
				} else {
					Node section = new RawNode(fenceBlock.getLiteral() + "\n");
					fenceBlock.insertAfter(section);
					fenceBlock.unlink();
				}

			}

		}

		@Override
		public void visit(Code code) {
			Node gray = new AnsiNode(AnsiEscape.GRAY);
			code.insertAfter(gray);
			code.unlink();
			gray.appendChild(new RawNode(code.getLiteral()));
		}

	}

	public String rerender(String markdownString) {
		Parser parser = Parser.builder().build();
		MarkdownRenderer renderer = MarkdownRenderer.builder()
				.nodeRendererFactory(new MarkdownNodeRendererFactory() {
					@Override
					public Set<Character> getSpecialCharacters() {
						return Set.of();
					}

					@Override
					public NodeRenderer create(MarkdownNodeRendererContext context) {
						return new AnsiRenderer(context);
					}
				})
				.build();
		Node document = parser.parse(markdownString);
		document.accept(new AnsiVisitor());
		return renderer.render(document);
	}
}
